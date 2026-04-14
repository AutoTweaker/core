package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

private fun toPendingToolCalls(
    toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
    timestamp: Instant,
    model: Model,
): List<AgentContext.CurrentRound.PendingToolCall>? {
    if (toolCalls.isNullOrEmpty()) return null
    return toolCalls.map {
        AgentContext.CurrentRound.PendingToolCall(
            callId = it.id,
            name = it.name,
            arguments = it.arguments,
            timestamp = timestamp,
            model = model,
        )
    }
}

/**
 * 非流式调用。返回 [Flow]，每次遇到错误时 emit [AgentChatResult.Failed]，
 * 最终成功时 emit [AgentChatResult.Success]。
 */
suspend fun agentChat(request: AgentChatRequest): Flow<AgentChatResult> = flow {
    val chatRequest = request.toChatRequest().copy(stream = false)

    val results = resilientChat(
        model = request.model,
        fallbackModels = request.fallbackModels,
        request = chatRequest,
    )

    var lastMessage: ChatMessage.AssistantMessage? = null
    var lastFinishReason: ChatResult.FinishReason? = null
    var lastUsage: Usage? = null
    val errors = mutableListOf<Error>()

    try {
        results.collect { resilientResult ->
            val result = resilientResult.result
            val msg = result.message

            if (msg is ChatMessage.ErrorMessage) {
                errors += Error(
                    content = msg.content,
                    statusCode = msg.statusCode,
                    retrying = resilientResult.retrying,
                    timestamp = msg.createdAt,
                )
                emit(AgentChatResult.Failed(error = errors.last()))
                return@collect
            }

            if (msg is ChatMessage.AssistantMessage) {
                lastMessage = msg
            }
            result.finishReason?.let { lastFinishReason = it }
            result.usage?.let { lastUsage = it }
        }
    } catch (_: IllegalStateException) {
        // 所有候选模型耗尽
    }

    val msg = lastMessage

    emit(
        AgentChatResult.Success(
            context = AgentContext.Message.Assistant(
                reasoning = msg?.reasoningContent,
                content = msg?.content,
                model = request.model,
                timestamp = msg?.createdAt ?: Clock.System.now(),
            ),
            toolCalls = toPendingToolCalls(msg?.toolCalls, msg?.createdAt ?: Clock.System.now(), request.model),
            usage = lastUsage,
            finishReason = lastFinishReason,
        )
    )
}

/**
 * 流式调用。返回 [Flow]，依次 emit [AgentChatStreamResult.Reasoning]、
 * [AgentChatStreamResult.Outputting]、[AgentChatStreamResult.Finished]。
 */
suspend fun agentChatStream(request: AgentChatRequest): Flow<AgentChatStreamResult> = flow {
    val chatRequest = request.toChatRequest().copy(stream = true)

    val results = resilientChat(
        model = request.model,
        fallbackModels = request.fallbackModels,
        request = chatRequest,
    )

    var reasoningContent = ""
    var content = ""
    val errors = mutableListOf<Error>()

    results.collect { resilientResult ->
        val result = resilientResult.result
        val msg = result.message

        if (msg is ChatMessage.ErrorMessage) {
            errors += Error(
                content = msg.content,
                statusCode = msg.statusCode,
                retrying = resilientResult.retrying,
                timestamp = msg.createdAt,
            )
            emit(AgentChatStreamResult.Failing(error = errors.toList()))
            return@collect
        }

        val assistantMsg = msg as? ChatMessage.AssistantMessage ?: return@collect

        if (assistantMsg.reasoningContent != null) {
            reasoningContent += assistantMsg.reasoningContent
            emit(AgentChatStreamResult.Reasoning(reasoningContent))
        }

        if (assistantMsg.content != null) {
            content += assistantMsg.content
            emit(
                AgentChatStreamResult.Outputting(
                    reasoningContent = reasoningContent.ifEmpty { null },
                    content = content,
                )
            )
        }

        if (result.finishReason != null) {
            val finalMsg = AgentContext.Message.Assistant(
                reasoning = reasoningContent.ifEmpty { null },
                content = content.ifEmpty { null },
                model = request.model,
                timestamp = assistantMsg.createdAt,
            )

            emit(
                AgentChatStreamResult.Finished(
                    result = AgentChatResult.Success(
                        context = finalMsg,
                        toolCalls = toPendingToolCalls(assistantMsg.toolCalls, assistantMsg.createdAt, request.model),
                        usage = result.usage,
                        finishReason = result.finishReason,
                    )
                )
            )
        }
    }
}
