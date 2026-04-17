package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.*
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.data.settings.getValue
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

class Agent(
    context: AgentContext,
    model: Model,
    fallbackModels: List<Model>?,
    thinking: Boolean,
    settings: List<SettingItem>,
) {
    //上下文
    private var currentContext: AgentContext = context

    private val _settings = settings

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

    //处理输入
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
                archiveCurrentRound()
                _status.value = AgentStatus.FREE
            }

            else -> { /* 其他指令 */
            }
        }
    }

    //处理用户消息
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
                is StreamProcessResult.Completed -> {
                    archiveCurrentRound()
                    _status.value = AgentStatus.FREE
                }

                is StreamProcessResult.ToolCallsRequired -> _status.value = AgentStatus.TOOL_CALLING
                is StreamProcessResult.Cancelled -> {
                    archiveCurrentRound()
                    _status.value = AgentStatus.FREE
                }

                is StreamProcessResult.Failed -> _status.value = AgentStatus.ERROR
            }
        }
    }

    fun dispatch(command: AgentCommand) {
        commandChannel.trySend(command)
    }

    private fun archiveCurrentRound() {
        val round = currentContext.currentRound ?: return

        //LLM推理时
        if (round.assistantMessage == null) {
            //新round
            if (round.turns.isNullOrEmpty()) {
                currentContext = currentContext.copy(currentRound = null)
                return
            }
            //已有工具调用（请求-响应）的round
            val completed = AgentContext.CompletedRound(
                userMessage = round.userMessage,
                turns = round.turns,
                finalAssistantMessage = null,
            )
            currentContext = currentContext.copy(
                currentRound = null,
                historyRounds = currentContext.historyRounds.orEmpty() + completed,
            )
            return
        }

        //有未处理工具调用
        val canceledToolTurns = round.pendingToolCalls?.map { call ->
            AgentContext.Turn(
                assistantMessage = null,
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
                            content = (_settings.getValue(TOOL_CANCELED_KEY) as SettingItem.Value.ValString).value,
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

    companion object {
        private val TOOL_CANCELED_KEY = SettingKey("core.agent.tool.response.canceled")
    }
}
