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

            //else -> { /* 其他指令 */
            //}
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
            try {
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
                agentChat(request).collect { result ->
                    when (result) {
                        is AgentChatStreamResult.Reasoning -> {
                            _output.emit(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.REASONING, result))
                        }

                        is AgentChatStreamResult.Outputting -> {
                            _output.emit(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.OUTPUTTING, result))
                        }

                        is AgentChatStreamResult.Failing -> {
                            val retrying = result.errors.lastOrNull()?.retrying
                            if (retrying != null) {
                                //即将重试
                                _status.value = AgentStatus.RETRYING
                                _output.emit(
                                    AgentOutput.StreamMessage(
                                        AgentOutput.StreamMessage.Status.RETRYING,
                                        result
                                    )
                                )
                            } else {
                                //重试次数耗尽
                                _status.value = AgentStatus.ERROR
                                _output.emit(
                                    AgentOutput.Error(
                                        result.errors.lastOrNull()?.content ?: "All retries exhausted",
                                        AgentOutput.Error.Type.LLM
                                    )
                                )
                            }
                        }

                        is AgentChatStreamResult.Finished -> {
                            handleFinished(result.result)
                        }
                    }
                }
            } catch (e: CancellationException) {
                //协程取消
                _status.value = AgentStatus.FREE
            } catch (e: Exception) {
                //捕获错误
                _status.value = AgentStatus.ERROR
                _output.emit(AgentOutput.Error(buildString {
                    append(e::class.simpleName ?: e::class.qualifiedName ?: "UnknownException")
                    e.message?.let { append(": ").append(it) }
                    val cause = e.cause
                    if (cause != null) append(" (caused by ").append(
                        cause::class.simpleName ?: cause::class.qualifiedName
                    ).append(")")
                }, AgentOutput.Error.Type.LLM))
            } finally {
                //重置状态
                if (_status.value == AgentStatus.PROCESSING || _status.value == AgentStatus.RETRYING) {
                    _status.value = AgentStatus.FREE
                }
            }
        }
    }

    private suspend fun handleFinished(result: AgentChatStreamResult.Finished.Result) {
        val updatedRound = currentContext.currentRound?.copy(
            assistantMessage = result.context,
            pendingToolCalls = result.toolCalls
        )
        currentContext = currentContext.copy(currentRound = updatedRound)

        _output.emit(
            AgentOutput.StreamMessage(
                AgentOutput.StreamMessage.Status.FINISHED,
                AgentChatStreamResult.Finished(result)
            )
        )

        if (!result.toolCalls.isNullOrEmpty()) {
            _status.value = AgentStatus.TOOL_CALLING
            _output.emit(AgentOutput.ToolCallRequest(result.toolCalls))
        } else {
            _status.value = AgentStatus.FREE
        }
    }

    fun dispatch(command: AgentCommand) {
        commandChannel.trySend(command)
    }
}
