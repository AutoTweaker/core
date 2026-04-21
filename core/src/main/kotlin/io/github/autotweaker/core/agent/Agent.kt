package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatRequest
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
//TODO 封装从settings取值的逻辑

@Suppress("unused")
class Agent(
	context: AgentContext,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	settings: List<SettingItem>,
	tools: List<Tool<*, *>>,
) {
	private val toolCancelledMessage: String = settings.find("core.agent.tool.response.canceled")
	
	//上下文
	private var currentContext: AgentContext = context
	
	private val _tools = tools
	
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
		// 监控输入
		scope.launch {
			for (command in commandChannel) {
				handleCommand(command)
			}
		}
		// 监控工作触发信号
		scope.launch {
			for (signal in workTrigger) {
				resumeFromCurrentState()
			}
		}
	}
	
	//处理输入
	private fun handleCommand(command: AgentCommand) {
		when (command) {
			is AgentCommand.SendMessage -> {
				processUserMessage(command.content, command.images)
			}
			
			is AgentCommand.UpdateModel -> {
				this.currentModel = command.model
				command.fallbackModels?.let { this.currentFallbackModels = it }
				command.thinking?.let { this.currentThinking = it }
			}
			
			is AgentCommand.ApproveToolCall -> {
				// TODO 处理工具审批
			}
			
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
	
	/**
	 * 从当前上下文状态继续工作
	 * 判断下一步动作（请求LLM或执行工具）并分发
	 */
	private fun resumeFromCurrentState() {
		when (val action = detectNextAction()) {
			NextAction.IDLE -> {
				_status.value = AgentStatus.FREE
			}
			
			NextAction.REQUEST_LLM -> requestLlm()
			NextAction.EXECUTE_TOOLS -> executeTools()
		}
	}
	
	/** 根据当前上下文状态判断下一步动作 */
	private fun detectNextAction(): NextAction {
		val round = currentContext.currentRound ?: return NextAction.IDLE
		if (round.pendingToolCalls != null) return NextAction.EXECUTE_TOOLS
		if (round.turns?.lastOrNull()?.tools?.isNotEmpty() == true) return NextAction.REQUEST_LLM
		if (round.turns == null) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	/** 请求LLM */
	private fun requestLlm() {
		reasoningJob = scope.launch {
			_status.value = AgentStatus.PROCESSING
			val request = AgentChatRequest(
				model = currentModel,
				fallbackModels = currentFallbackModels,
				thinking = currentThinking,
				tools = _tools.takeIf { it.isNotEmpty() }?.let { toolList ->
					toolList.flatMap { tool ->
						tool.functions.map { func ->
							ChatRequest.Tool(
								name = func.name,
								description = func.description,
								parameters = func.parameters.toChatRequestParameters(),
							)
						}
					}
				},
				context = currentContext
			)
			when (val result = streamProcessor.process(request, currentContext)) {
				is StreamProcessResult.Completed -> {
					archiveCurrentRound()
					_status.value = AgentStatus.FREE
				}
				
				is StreamProcessResult.ToolCallsRequired -> {
					// 状态由executeTools根据是否需要审批决定
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
	
	/** 将Tool.Function的parameters转换为ChatRequest.Tool.Parameters */
	private fun Map<String, Tool.Function.Property>.toChatRequestParameters(): ChatRequest.Tool.Parameters {
		val properties = mapValues { (_, prop) ->
			ChatRequest.Tool.Parameters.Property(
				type = when (prop.value) {
					is Tool.Function.Property.Value.StringValue -> ChatRequest.Tool.Parameters.Property.Type.STRING
					is Tool.Function.Property.Value.NumberValue -> ChatRequest.Tool.Parameters.Property.Type.NUMBER
					is Tool.Function.Property.Value.IntegerValue -> ChatRequest.Tool.Parameters.Property.Type.INTEGER
					is Tool.Function.Property.Value.BooleanValue -> ChatRequest.Tool.Parameters.Property.Type.BOOLEAN
					is Tool.Function.Property.Value.ArrayValue -> ChatRequest.Tool.Parameters.Property.Type.ARRAY
					is Tool.Function.Property.Value.ObjectValue -> ChatRequest.Tool.Parameters.Property.Type.OBJECT
				},
				description = prop.description,
				enum = when (val v = prop.value) {
					is Tool.Function.Property.Value.StringValue -> v.enum?.map {
						kotlinx.serialization.json.JsonPrimitive(
							it
						)
					}
					
					is Tool.Function.Property.Value.NumberValue -> v.enum?.map {
						kotlinx.serialization.json.JsonPrimitive(
							it
						)
					}
					
					is Tool.Function.Property.Value.IntegerValue -> v.enum?.map {
						kotlinx.serialization.json.JsonPrimitive(
							it
						)
					}
					
					else -> null
				},
			)
		}
		val required = filter { it.value.required }.keys.toList().ifEmpty { null }
		return ChatRequest.Tool.Parameters(properties = properties, required = required)
	}
	
	/** 执行工具调用 */
	private fun executeTools() {
		scope.launch {
			_status.value = AgentStatus.TOOL_CALLING
			// TODO 执行pendingToolCalls
			// TODO 将结果写入Turn.tools，清空pendingToolCalls
			// TODO 然后workTrigger.trySend(Unit)
		}
	}
	
	/** 下一步动作枚举 */
	private enum class NextAction {
		IDLE,
		REQUEST_LLM,
		EXECUTE_TOOLS,
	}
	
	fun dispatch(command: AgentCommand) {
		commandChannel.trySend(command)
	}
	
	private fun archiveCurrentRound() {
		val round = currentContext.currentRound ?: return
		
		//新round，无任何内容（无assistantMessage、无turns、无pendingToolCalls）
		if (round.assistantMessage == null && round.turns.isNullOrEmpty() && round.pendingToolCalls.isNullOrEmpty()) {
			currentContext = currentContext.copy(currentRound = null)
			return
		}
		
		//将未处理的pendingToolCalls转为CANCELLED的Turn
		val canceledToolTurns = round.pendingToolCalls?.map { call ->
			AgentContext.Turn(
				assistantMessage = round.assistantMessage,
				tools = listOf(
					AgentContext.Message.Tool(
						name = call.name,
						call = AgentContext.Message.Tool.Call(
							arguments = call.arguments,
							timestamp = call.timestamp,
							model = call.model,
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
		
		val allTurns = buildList {
			round.turns?.let { addAll(it) }
			canceledToolTurns?.let { addAll(it) }
		}.ifEmpty { null }
		
		val completed = AgentContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = round.assistantMessage,
		)
		currentContext = currentContext.copy(
			currentRound = null,
			historyRounds = currentContext.historyRounds.orEmpty() + completed,
		)
	}
}
