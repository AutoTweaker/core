package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.workspace.Workspace
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

//TODO 完善handleCommand处理全部指令
//TODO Stop应为终止并回到FREE，Cancel应为中断工具运行或compact但继续推理
//TODO 通过Retry重试出错时消息，明确区分出ERROR与FREE状态
//TODO for (command in commandChannel) 应该区分优先级，例如新的Stop比正在等待的SendMessage优先级高，另外确保Stop/Cancel类指令能立即响应不被阻塞
//TODO 实现上下文更新输出，区分自动更新、上下文压缩、LLM出错导致用户消息被回退
//TODO AgentContext的生命周期管理，应该抽成一个独立组件AgentContextManager

@Suppress("unused")
class Agent(
	context: AgentContext,
	workspace: Workspace,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	settings: List<SettingItem>,
	tools: List<Tool>,
) {
	//提取设置条目
	private val toolCancelledMessage: String = settings.find("core.agent.tool.response.canceled")
	private val toolRejectedMessage: String = settings.find("core.agent.tool.response.rejected")
	private val toolRejectedWithFeedbackMessage: String =
		settings.find("core.agent.tool.response.rejected.with.feedback")
	
	//设置
	private val _settings: List<SettingItem> = settings
	
	//上下文
	private var currentContext: AgentContext = context
	
	//工具列表
	private val _tools = Tools(settings).also { t -> tools.forEach { t.add(it) } }
	
	//工作区
	private val _workspace: Workspace = workspace
	
	//模型数据
	private var currentModel = model
	private var currentFallbackModels = fallbackModels
	private var currentThinking: Boolean = thinking
	
	//当前工作协程
	private var currentJob: Job? = null
	
	//状态
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val status: StateFlow<AgentStatus> = _status.asStateFlow()
	
	//输出
	private val _output = MutableSharedFlow<AgentOutput>()
	val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	//控制输入
	private val commandChannel = Channel<AgentCommand>(Channel.UNLIMITED)
	
	//工作触发信号
	private val workTrigger = Channel<Unit>(Channel.CONFLATED)
	
	//llm输出转agentOutput
	private val streamProcessor = AgentStreamProcessor(
		output = _output,
		onStatusChange = { _status.value = it },
		onContextUpdate = { currentContext = it },
	)
	
	//协程
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	//待审批toolCall
	private var pendingApproval: List<Tools.ToolCallResolveResult.NeedsApproval>? = null
	
	//已处理toolCall
	private var processedTools: List<AgentContext.Message.Tool>? = null
	
	//初始化
	init {
		startEventLoop()
	}
	
	//启动协程
	private fun startEventLoop() {
		//监控输入
		scope.launch {
			for (command in commandChannel) {
				handleCommand(command)
			}
		}
		//监控工作触发信号
		scope.launch {
			for (signal in workTrigger) {
				resumeFromCurrentState()
			}
		}
	}
	
	//处理输入
	private fun handleCommand(command: AgentCommand) {
		when (command) {
			//接收用户消息
			is AgentCommand.SendMessage -> {
				processUserMessage(command.content, command.images)
			}
			
			//更新模型
			is AgentCommand.UpdateModel -> {
				this.currentModel = command.model
				command.fallbackModels?.let { this.currentFallbackModels = it }
				command.thinking?.let { this.currentThinking = it }
			}
			
			//工具批准
			is AgentCommand.ApproveToolCall -> {
				handleApproveToolCall(command)
			}
			
			//停止
			AgentCommand.Stop -> {
				currentJob?.cancel()
				currentJob = null
				archiveCurrentRound()
				_status.value = AgentStatus.FREE
			}
			
			AgentCommand.Pause -> TODO("Pause")
			AgentCommand.Resume -> TODO("Resume")
			AgentCommand.Cancel -> TODO("Cancel")
			AgentCommand.Retry -> TODO("Retry")
			AgentCommand.Compact -> TODO("Compact")
		}
	}
	
	//处理用户消息
	private fun processUserMessage(content: String, images: List<Base64>? = null) {
		if (_status.value != AgentStatus.FREE) return
		
		//更新上下文
		val userMsg = AgentContext.Message.User(
			summarizedMessage = null,
			content = content,
			images = images,
			timestamp = Clock.System.now()
		)
		currentContext = currentContext.copy(
			currentRound = AgentContext.CurrentRound(userMessage = userMsg, turns = null)
		)
		
		//从当前状态继续工作
		workTrigger.trySend(Unit)
	}
	
	//从当前状态继续
	private fun resumeFromCurrentState() {
		when (val action = detectNextAction()) {
			NextAction.IDLE -> {
				_status.value = AgentStatus.FREE
			}
			
			NextAction.REQUEST_LLM -> requestLlm()
			NextAction.EXECUTE_TOOLS -> executeTools()
		}
	}
	
	//根据AgentContext判断下一步动作
	private fun detectNextAction(): NextAction {
		val round = currentContext.currentRound ?: return NextAction.IDLE
		if (round.pendingToolCalls != null) return NextAction.EXECUTE_TOOLS
		if (round.turns?.lastOrNull()?.tools?.isNotEmpty() == true) return NextAction.REQUEST_LLM
		if (round.turns.isNullOrEmpty()) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	//调用llm
	private fun requestLlm() {
		currentJob = scope.launch {
			//更新状态
			_status.value = AgentStatus.PROCESSING
			//构建请求
			val request = AgentChatRequest(
				model = currentModel,
				fallbackModels = currentFallbackModels,
				thinking = currentThinking,
				tools = _tools.assembleTools(),
				context = currentContext
			)
			//处理响应
			when (val result = streamProcessor.process(request, currentContext)) {
				is StreamProcessResult.Completed -> {
					archiveCurrentRound()
					_status.value = AgentStatus.FREE
				}
				
				is StreamProcessResult.ToolCallsRequired -> {
					workTrigger.trySend(Unit)
				}
				
				is StreamProcessResult.Cancelled -> {
					archiveCurrentRound()
					_status.value = AgentStatus.FREE
				}
				
				is StreamProcessResult.Failed -> {
					_status.value = AgentStatus.ERROR
				}
			}
		}
	}
	
	//处理工具调用
	private fun executeTools() {
		currentJob = scope.launch {
			//更新状态
			_status.value = AgentStatus.PROCESSING
			
			//读取当前轮次数据
			val round = currentContext.currentRound ?: return@launch
			val pendingCalls = round.pendingToolCalls ?: return@launch
			val assistantMsg = requireNotNull(round.assistantMessage)
			
			//校验工具参数
			val results = _tools.resolveToolCalls(pendingCalls)
			//以callId作为键存储PendingToolCall
			val callById = pendingCalls.associateBy { it.callId }
			
			//提取校验失败的工具
			val failures = results.filterIsInstance<Tools.ToolCallResolveResult.ParseFailure>()
			//提取校验成功的工具
			val needsApproval = results.filterIsInstance<Tools.ToolCallResolveResult.NeedsApproval>()
			
			//校验失败的构建响应
			val errorTools = failures.map { f ->
				buildErrorTool(callById.getValue(f.callId), f.errorMessage)
			}
			//更新已处理工具的列表
			processedTools = errorTools
			
			//清理pendingToolCalls中已处理项目
			if (errorTools.isNotEmpty()) {
				keepPendingCalls(needsApproval.map { it.callId }.toSet())
			}
			
			//检查是否有校验成功的工具
			if (needsApproval.isNotEmpty()) {
				//更新未处理工具的列表
				pendingApproval = needsApproval
				//构建工具请求
				val needsApprovalCalls = needsApproval.map { callById.getValue(it.callId) }
				//发送请求
				_output.tryEmit(AgentOutput.ToolCallRequest(needsApprovalCalls))
				//更新状态
				_status.value = AgentStatus.WAITING
			} else {
				//全部校验失败，无需审批，直接写Turn并继续
				writeToolTurn(assistantMsg)
			}
		}
	}
	
	//处理工具调用批准
	private fun handleApproveToolCall(command: AgentCommand.ApproveToolCall) {
		//更新状态
		_status.value = AgentStatus.PROCESSING
		//读取待审批toolCall
		val needs = pendingApproval ?: return
		
		//启动协程
		currentJob = scope.launch {
			//读取上下文
			val round = currentContext.currentRound ?: return@launch
			val pendingCalls = round.pendingToolCalls ?: return@launch
			val assistantMsg = requireNotNull(round.assistantMessage)
			//以callId作为键存储PendingToolCall
			val callById = pendingCalls.associateBy { it.callId }
			//以callId作为键存储Approve
			val approvalByCallId = command.approvals.associateBy { it.callId }
			
			//存储未处理的
			val remaining = mutableListOf<Tools.ToolCallResolveResult.NeedsApproval>()
			//存储已处理的
			val processed = mutableListOf<AgentContext.Message.Tool>()
			
			//遍历待审批toolCall
			for (n in needs) {
				//根据callId读取PendingToolCall
				val call = callById.getValue(n.callId)
				//根据callId读取Approve
				val a = approvalByCallId[n.callId]
				//若当前（遍历到的）toolCall未批准
				if (a == null) {
					//存入未处理列表
					remaining.add(n)
					//进入下一次循环
					continue
				}
				//上一个if未匹配到，那么当前toolCall已经批准（或拒绝
				processed.add(
					//若批准，直接调用
					if (a.approved) executeApprovedTool(n.result, call)
					//未批准则构建拒绝消息
					else buildRejectedTool(call, a.reason)
				)
			}
			
			//将未处理toolCall继续存入pendingApproval
			pendingApproval = remaining.ifEmpty { null }
			
			//更新已处理工具列表，等待存入上下文
			processedTools = (processedTools.orEmpty() + processed)
			//清理pendingToolCalls的已处理项
			keepPendingCalls(remaining.map { it.callId }.toSet())
			
			//如果待处理列表为空，存入上下文
			if (remaining.isEmpty()) writeToolTurn(assistantMsg)
		}
	}
	
	//实际调用工具
	private suspend fun executeApprovedTool(
		result: ToolCallValidator.ValidationResult.Success,
		call: AgentContext.CurrentRound.PendingToolCall,
	): AgentContext.Message.Tool {
		TODO("工具执行")
	}
	
	//构建错误工具消息
	private fun buildErrorTool(
		call: AgentContext.CurrentRound.PendingToolCall,
		errorMsg: String,
	): AgentContext.Message.Tool = AgentContext.Message.Tool(
		name = call.name,
		call = AgentContext.Message.Tool.Call(
			arguments = call.arguments,
			reason = call.reason,
			timestamp = call.timestamp,
			model = call.model,
		),
		callId = call.callId,
		result = AgentContext.Message.Tool.Result(
			content = errorMsg,
			timestamp = Clock.System.now(),
			status = AgentContext.Message.Tool.Result.Status.FAILURE,
		),
	)
	
	//构建拒绝工具的消息
	private fun buildRejectedTool(
		call: AgentContext.CurrentRound.PendingToolCall,
		feedbackReason: String?,
	): AgentContext.Message.Tool = AgentContext.Message.Tool(
		name = call.name,
		call = AgentContext.Message.Tool.Call(
			arguments = call.arguments,
			reason = call.reason,
			timestamp = call.timestamp,
			model = call.model,
		),
		callId = call.callId,
		result = AgentContext.Message.Tool.Result(
			content = if (feedbackReason != null)
				toolRejectedWithFeedbackMessage.format(feedbackReason)
			else
				toolRejectedMessage,
			timestamp = Clock.System.now(),
			status = AgentContext.Message.Tool.Result.Status.CANCELLED,
		),
	)
	
	//根据callId从上下文的pendingToolCalls移除制定工具
	private fun keepPendingCalls(callIds: Set<String>) {
		val round = currentContext.currentRound ?: return
		val pending = round.pendingToolCalls ?: return
		currentContext = currentContext.copy(
			currentRound = round.copy(
				pendingToolCalls = pending.filter { it.callId in callIds }.ifEmpty { null }
			)
		)
	}
	
	//将已处理工具写入上下文并继续Agent迭代
	private fun writeToolTurn(assistantMsg: AgentContext.Message.Assistant) {
		val tools = processedTools.orEmpty()
		processedTools = null
		if (tools.isEmpty()) return
		val round = currentContext.currentRound ?: return
		currentContext = currentContext.copy(
			currentRound = round.copy(
				turns = (round.turns ?: emptyList()) + AgentContext.Turn(assistantMsg, tools),
			)
		)
		workTrigger.trySend(Unit)
	}

	//下一步动作
	private enum class NextAction {
		IDLE,
		REQUEST_LLM,
		EXECUTE_TOOLS,
	}
	
	//外部发送命令
	fun dispatch(command: AgentCommand) {
		commandChannel.trySend(command)
	}
	
	//存档currentRound
	private fun archiveCurrentRound() {
		val round = currentContext.currentRound ?: return
		
		//新round，回退用户消息
		if (round.assistantMessage == null && round.turns.isNullOrEmpty() && round.pendingToolCalls.isNullOrEmpty()) {
			currentContext = currentContext.copy(currentRound = null)
			return
		}
		val assistantMsg = round.assistantMessage ?: return
		
		//将未处理的pendingToolCalls转为CANCELLED的工具
		val canceledTools = round.pendingToolCalls?.map { call ->
			AgentContext.Message.Tool(
				name = call.name,
				call = AgentContext.Message.Tool.Call(
					arguments = call.arguments,
					timestamp = call.timestamp,
					model = call.model,
					reason = call.reason,
				),
				callId = call.callId,
				result = AgentContext.Message.Tool.Result(
					content = toolCancelledMessage,
					timestamp = Clock.System.now(),
					status = AgentContext.Message.Tool.Result.Status.CANCELLED,
				),
			)
		}
		
		pendingApproval = null
		
		//将已处理和未处理的工具都存入同一个Turn
		val archivedTools = (processedTools.orEmpty() + canceledTools.orEmpty())
		val archivedTurn = archivedTools.takeIf { it.isNotEmpty() }?.let {
			AgentContext.Turn(assistantMsg, it)
		}
		processedTools = null

		//构建turns列表
		val allTurns = buildList {
			round.turns?.let { addAll(it) }
			archivedTurn?.let { add(it) }
		}.ifEmpty { null }
		
		//构建CompletedRound
		val completed = AgentContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = assistantMsg,
		)
		
		//更新上下文
		currentContext = currentContext.copy(
			currentRound = null,
			historyRounds = currentContext.historyRounds.orEmpty() + completed,
		)
	}
}
