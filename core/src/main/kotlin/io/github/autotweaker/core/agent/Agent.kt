package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.tool.AutoApprovalRules
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

//TODO 完善handleCommand处理全部指令
//TODO Stop应为终止并回到FREE，Cancel应为中断工具运行或compact但继续推理
//TODO 通过Retry重试出错时消息，明确区分出ERROR与FREE状态
//TODO for (command in commandChannel) 应该区分优先级，例如新的Stop比正在等待的SendMessage优先级高，另外确保Stop/Cancel类指令能立即响应不被阻塞
//TODO 实现工具调用相关支持，分离出agent.tool模块专门准备工具依赖，实现工具自动审批
//TODO 实现上下文更新输出，区分自动更新、上下文压缩、LLM出错导致用户消息被回退
//TODO AgentContext的生命周期管理，应该抽成一个独立组件AgentContextManager

@Suppress("unused")
class Agent(
	context: AgentContext,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	settings: List<SettingItem>,
	tools: List<Tool<*, *>>,
	autoApprovalRules: AutoApprovalRules,
) {
	private val toolCancelledMessage: String = settings.find("core.agent.tool.response.canceled")
	private val _settings: List<SettingItem> = settings

	//上下文
	private var currentContext: AgentContext = context
	
	private val _tools = Tools(settings).also { t -> tools.forEach { t.add(it) } }
	private val _autoApprovalRules = autoApprovalRules
	
	//模型
	private var currentModel = model
	private var currentFallbackModels = fallbackModels
	private var currentThinking: Boolean = thinking
	
	//当前推理协程
	private var reasoningJob: Job? = null
	
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
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	init {
		startEventLoop()
	}
	
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
				// TODO 处理工具审批
			}
			
			//停止
			AgentCommand.Stop -> {
				reasoningJob?.cancel()
				archiveCurrentRound()
				_status.value = AgentStatus.FREE
			}
			
			else -> { /* 其他指令 */
			}
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
		if (round.turns == null) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	//调用llm
	private fun requestLlm() {
		reasoningJob = scope.launch {
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
	
	//调用工具
	private fun executeTools() {
		scope.launch {
			_status.value = AgentStatus.TOOL_CALLING
			
			//获取当前轮次
			val round = currentContext.currentRound ?: return@launch
			//获取待处理工具调用
			val pendingCalls = round.pendingToolCalls ?: return@launch
			
			val turns = pendingCalls.map { call ->
				// TODO 通过 ToolInput 和 SimpleContainer 执行工具
				val toolMessage = AgentContext.Message.Tool(
					name = call.name,
					call = AgentContext.Message.Tool.Call(
						arguments = call.arguments,
						reason = call.reason,
						timestamp = call.timestamp,
						model = call.model,
					),
					callId = call.callId,
					result = AgentContext.Message.Tool.Result(
						content = "Tool execution not yet implemented",
						timestamp = Clock.System.now(),
						status = AgentContext.Message.Tool.Result.Status.FAILURE,
					),
				)
				
				AgentContext.Turn(
					assistantMessage = requireNotNull(round.assistantMessage) { "assistantMessage must not be null when executing tools" },
					tools = listOf(toolMessage),
				)
			}
			
			//更新上下文
			currentContext = currentContext.copy(
				currentRound = round.copy(
					turns = (round.turns ?: emptyList()) + turns,
					pendingToolCalls = null,
				)
			)
			
			workTrigger.trySend(Unit)
		}
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
		
		//将未处理的pendingToolCalls转为CANCELLED的Turn
		val canceledToolTurns = round.pendingToolCalls?.map { call ->
			AgentContext.Turn(
				assistantMessage = requireNotNull(round.assistantMessage) { "round.assistantMessage must not be null when archiving" },
				tools = listOf(
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
				),
			)
		}
		
		//构建turns列表
		val allTurns = buildList {
			round.turns?.let { addAll(it) }
			canceledToolTurns?.let { addAll(it) }
		}.ifEmpty { null }
		
		//构建CompletedRound
		val completed = AgentContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = requireNotNull(round.assistantMessage) { "round.assistantMessage must not be null when archiving" },
		)
		
		//更新上下文
		currentContext = currentContext.copy(
			currentRound = null,
			historyRounds = currentContext.historyRounds.orEmpty() + completed,
		)
	}
}
