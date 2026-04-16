package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.Base64
import io.github.whiteelephant.autotweaker.core.agent.llm.*
import io.github.whiteelephant.autotweaker.core.data.database.settings.SettingItem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

//TODO 在任何状态变为FREE之前，确保currentRound移动到historyRounds
//TODO 完善handleCommand处理全部指令
//TODO Stop应为终止并回到FREE，Cancel应为中断工具运行或compact但继续推理
//TODO 通过Retry重试出错时消息，明确区分出ERROR与FREE状态
//TODO for (command in commandChannel) 应该区分优先级，例如新的Stop比正在等待的SendMessage优先级高，另外确保Stop/Cancel类指令能立即响应不被阻塞
//TODO 实现工具调用相关支持，分离出agent.tool模块专门准备工具依赖，实现工具自动审批
//TODO 实现上下文更新输出，区分自动更新、上下文压缩、LLM出错导致用户消息被回退

class Agent(
    context: AgentContext,
    model: Model,
    fallbackModels: List<Model>?,
    thinking: Boolean,
    settings: List<SettingItem<*>>,
) {
    //上下文
    private var currentContext: AgentContext = context

    //模型
    private var currentModel: Model = model
    private var currentFallbackModels: List<Model>? = fallbackModels

    //推理开关
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
        scope.launch {
            //监控输入
            for (command in commandChannel) {
                handleCommand(command)
            }
        }
    }

    private suspend fun handleCommand(command: AgentCommand) {
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
                _status.value = AgentStatus.FREE
            }

            else -> { /* 其他指令 */
            }
        }
    }

    private suspend fun processUserMessage(content: String, images: List<Base64>? = null) {
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

        //调用LLM
        reasoningJob = scope.launch {
            //更新状态
            _status.value = AgentStatus.PROCESSING
            //准备请求
            val request = AgentChatRequest(
                model = currentModel,
                fallbackModels = currentFallbackModels,
                thinking = currentThinking,
                tools = null,
                context = currentContext
            )
            //调用LLM并收集输出
            when (val result = streamProcessor.process(request, currentContext)) {
                is StreamProcessResult.Completed -> _status.value = AgentStatus.FREE
                is StreamProcessResult.ToolCallsRequired -> _status.value = AgentStatus.TOOL_CALLING
                is StreamProcessResult.Cancelled -> _status.value = AgentStatus.FREE
                is StreamProcessResult.Failed -> _status.value = AgentStatus.ERROR
            }
        }
    }

    fun dispatch(command: AgentCommand) {
        commandChannel.trySend(command)
    }
}
