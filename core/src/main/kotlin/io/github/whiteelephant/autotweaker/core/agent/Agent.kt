package io.github.whiteelephant.autotweaker.core.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class Agent(
    val id: String,
    val systemPrompt: String?,
    // TODO context、tools 等
) {
    private val _status = MutableStateFlow(AgentStatus.FREE)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _output = MutableSharedFlow<AgentOutput>(replay = 1)
    val output: SharedFlow<AgentOutput> = _output.asSharedFlow()

    private val commandChannel = Channel<AgentCommand>(Channel.UNLIMITED)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startEventLoop()
    }

    private fun startEventLoop() {
        scope.launch {
            for (command in commandChannel) {
                handleCommand(command)
            }
        }
    }

    private suspend fun handleCommand(command: AgentCommand) {
        when (command) {
            is AgentCommand.SendMessage -> {
                processUserMessage(command.content)
            }
            is AgentCommand.ApproveToolCall -> {
                // 处理工具审批逻辑
            }
            AgentCommand.Stop -> {
                _status.value = AgentStatus.FREE
                // 取消所有正在运行的子任务
                scope.coroutineContext.cancelChildren()
            }
            // ... 处理其他命令
            else -> { /* 暂未实现 */ }
        }
    }

    private suspend fun processUserMessage(content: String) {
        if (_status.value != AgentStatus.FREE) return // 防止重复提交

        _status.value = AgentStatus.PROCESSING

        try {
            // 这里是以后对接 LLM 的地方
            // 模拟流式推理过程...
            delay(1000)

            // 假设推理完发现要调用工具
            _status.value = AgentStatus.TOOL_CALLING
            // 发射一个工具请求给 UI
            // _output.emit(AgentOutput.ToolCallRequest(...))

        } catch (e: Exception) {
            _status.value = AgentStatus.ERROR
            _output.emit(AgentOutput.Error(e.message ?: "Unknown", AgentOutput.Error.Type.LLM))
        }
    }

    fun dispatch(command: AgentCommand) {
        commandChannel.trySend(command)
    }
}
