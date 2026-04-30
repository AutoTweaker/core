package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.phase.*
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.workspace.Workspace
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlin.time.Clock

//TODO 完善handleCommand处理全部指令
//TODO Stop应为终止并回到FREE，Cancel应为中断工具运行或compact但继续推理
//TODO 通过Retry重试出错时消息，明确区分出ERROR与FREE状态
//TODO 实现上下文更新输出，区分自动更新、上下文压缩、LLM出错导致用户消息被回退
//TODO AgentContext的生命周期管理，应该抽成一个独立组件AgentContextManager

@Suppress("unused")
class Agent(
	override var context: AgentContext,
	override val workspace: Workspace,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	override val summarizeModel: Model,
	override val containerConfig: ContainerConfig,
	override val settings: List<SettingItem>,
	tools: List<Tool>,
) : AgentEnvironment {
	override val toolCancelledMessage: String = settings.find("core.agent.tool.response.canceled")
	override val toolRejectedMessage: String = settings.find("core.agent.tool.response.rejected")
	override val toolRejectedWithFeedbackMessage: String =
		settings.find("core.agent.tool.response.rejected.with.feedback")
	
	//可变状态
	override val agentState = MutableAgentState()
	
	//工具列表
	override val tools = Tools(settings).also { t -> tools.forEach { t.add(it) } }
	
	//模型数据
	override var currentModel: Model = model
	override var currentFallbackModels: List<Model>? = fallbackModels
	override var currentThinking: Boolean = thinking
	
	//当前工作协程
	private var currentJob: Job? = null
	
	//状态
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val status: StateFlow<AgentStatus> = _status.asStateFlow()
	
	//输出
	private val _output = MutableSharedFlow<AgentOutput>()
	val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	//双通道
	private val directiveChannel = Channel<AgentCommand.Directive>(Channel.UNLIMITED)
	private val messageChannel = Channel<AgentCommand.Message>(Channel.UNLIMITED)
	
	//工作触发信号
	private val workTrigger = Channel<Unit>(Channel.CONFLATED)
	
	//llm输出转agentOutput
	private val streamProcessor = AgentStreamProcessor(
		emitOutput = { _output.emit(it) },
		onStatusChange = { _status.value = it },
		onContextUpdate = { this.context = it },
	)
	
	//协程
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	//输出
	override suspend fun emitOutput(output: AgentOutput) {
		_output.emit(output)
	}
	
	//更新状态
	override fun updateStatus(status: AgentStatus) {
		_status.value = status
	}
	
	//初始化
	init {
		startEventLoop()
	}
	
	//启动协程
	private fun startEventLoop() {
		//命令处理
		scope.launch {
			while (isActive) {
				val directive = directiveChannel.tryReceive().getOrNull()
				if (directive != null) {
					handleDirective(directive)
					continue
				}
				select<Unit> {
					directiveChannel.onReceive { handleDirective(it) }
					messageChannel.onReceive { handleMessage(it) }
				}
			}
		}
		//工作触发信号
		scope.launch {
			for (signal in workTrigger) {
				resumeFromCurrentState()
			}
		}
	}
	
	//处理指令（即时
	private fun handleDirective(directive: AgentCommand.Directive) {
		when (directive) {
			is AgentCommand.Directive.Stop -> {
				//取消协程
				currentJob?.cancel()
				currentJob = null
				//归档上下文
				archiveCurrentRound(this)
				//更新状态
				updateStatus(AgentStatus.FREE)
			}
			
			is AgentCommand.Directive.UpdateModel -> {
				//更新模型
				currentModel = directive.model
				directive.fallbackModels?.let { currentFallbackModels = it }
				directive.thinking?.let { currentThinking = it }
			}
			
			is AgentCommand.Directive.Pause -> TODO("Pause")
			is AgentCommand.Directive.Resume -> TODO("Resume")
			is AgentCommand.Directive.Cancel -> TODO("Cancel")
			is AgentCommand.Directive.Retry -> {
				if (_status.value != AgentStatus.ERROR) return
				updateStatus(AgentStatus.FREE)
				workTrigger.trySend(Unit)
			}
			is AgentCommand.Directive.Compact -> TODO("Compact")
		}
	}
	
	//处理消息
	private fun handleMessage(message: AgentCommand.Message) {
		when (message) {
			is AgentCommand.Message.SendMessage -> {
				//如果非空闲丢弃消息
				if (_status.value != AgentStatus.FREE) return
				//处理消息
				processUserMessage(message.content, message.images)
			}
			
			is AgentCommand.Message.ApproveToolCall -> {
				//回去排队
				if (_status.value != AgentStatus.WAITING) {
					messageChannel.trySend(message)
					return
				}
				//没有待批准的了，丢弃消息
				if (agentState.pendingApproval == null) return
				//启动协程
				currentJob = scope.launch {
					//处理批准
					val result = handleApprovalPhase(this@Agent, message.approvals)
					when (result) {
						PhaseResult.Continue -> workTrigger.trySend(Unit)
						PhaseResult.Done -> {}
						PhaseResult.Error -> {}
					}
				}
			}
		}
	}
	
	//处理用户消息
	private fun processUserMessage(content: String, images: List<Base64>? = null) {
		//构建Message.User
		val userMsg = AgentContext.Message.User(
			summarizedMessage = null,
			content = content,
			images = images,
			timestamp = Clock.System.now()
		)
		//更新上下文
		context = context.copy(
			currentRound = AgentContext.CurrentRound(userMessage = userMsg, turns = null)
		)
		//开始迭代
		workTrigger.trySend(Unit)
	}
	
	//从当前状态继续
	private fun resumeFromCurrentState() {
		when (detectNextAction()) {
			NextAction.IDLE -> updateStatus(AgentStatus.FREE)
			NextAction.REQUEST_LLM -> requestLlm()
			NextAction.EXECUTE_TOOLS -> executeTools()
		}
	}
	
	//根据AgentContext判断下一步动作
	private fun detectNextAction(): NextAction {
		val round = context.currentRound ?: return NextAction.IDLE
		if (round.pendingToolCalls != null) return NextAction.EXECUTE_TOOLS
		if (round.turns?.lastOrNull()?.tools?.isNotEmpty() == true) return NextAction.REQUEST_LLM
		if (round.turns.isNullOrEmpty()) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	//调用llm
	private fun requestLlm() {
		//启动协程
		currentJob = scope.launch {
			//调用对应方法
			val result = requestLlmPhase(this@Agent, streamProcessor)
			//处理结果
			when (result) {
				PhaseResult.Continue -> workTrigger.trySend(Unit)
				PhaseResult.Done -> {}
				PhaseResult.Error -> {}
			}
		}
	}
	
	//处理工具调用
	private fun executeTools() {
		currentJob = scope.launch {
			//调用对应方法
			val result = validateToolCallsPhase(this@Agent)
			when (result) {
				PhaseResult.Continue -> workTrigger.trySend(Unit)
				PhaseResult.Done -> {}
				PhaseResult.Error -> {}
			}
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
		when (command) {
			is AgentCommand.Directive -> directiveChannel.trySend(command)
			is AgentCommand.Message -> messageChannel.trySend(command)
		}
	}
}
