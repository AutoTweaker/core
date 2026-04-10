package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.agent.llm.chat
import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

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
 * 非流式调用。返回最终的 [AgentChatResult]。
 */
suspend fun chat(request: AgentChatRequest): AgentChatResult {
    val chatRequest = request.toChatRequest().copy(stream = false)

    val results = chat(
        provider = request.model.provider.name,
        apiKey = request.model.provider.apiKey,
        baseUrl = request.model.provider.baseUrl,
        request = chatRequest,
    )

    var lastMessage: ChatMessage.AssistantMessage? = null
    var lastFinishReason: ChatResult.FinishReason? = null
    var lastUsage: Usage? = null

    results.collect { result ->
        val msg = result.message
        if (msg is ChatMessage.AssistantMessage) {
            lastMessage = msg
        }
        result.finishReason?.let { lastFinishReason = it }
        result.usage?.let { lastUsage = it }
    }

    val msg = lastMessage

    return AgentChatResult(
        context = AgentContext.Message.Assistant(
            reasoning = msg?.reasoningContent,
            content = msg?.content,
            model = request.model,
            timestamp = msg?.createdAt ?: Instant.now(),
        ),
        toolCalls = toPendingToolCalls(msg?.toolCalls, msg?.createdAt ?: Instant.now(), request.model),
        usage = lastUsage,
        finishReason = lastFinishReason,
    )
}

/**
 * 流式调用。返回 [Flow]，依次 emit [AgentChatStreamResult.Reasoning]、
 * [AgentChatStreamResult.Outputting]、[AgentChatStreamResult.Finished]。
 */
suspend fun chatStream(request: AgentChatRequest): Flow<AgentChatStreamResult> = flow {
    val chatRequest = request.toChatRequest().copy(stream = true)

    val results = chat(
        provider = request.model.provider.name,
        apiKey = request.model.provider.apiKey,
        baseUrl = request.model.provider.baseUrl,
        request = chatRequest,
    )

    var reasoningContent = ""
    var content = ""

    results.collect { result: ChatResult ->
        val msg = result.message as? ChatMessage.AssistantMessage ?: return@collect

        if (msg.reasoningContent != null) {
            reasoningContent += msg.reasoningContent
            emit(AgentChatStreamResult.Reasoning(reasoningContent))
        }

        if (msg.content != null) {
            content += msg.content
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
                timestamp = msg.createdAt,
            )

            emit(
                AgentChatStreamResult.Finished(
                    result = AgentChatResult(
                        context = finalMsg,
                        toolCalls = toPendingToolCalls(msg.toolCalls, msg.createdAt, request.model),
                        usage = result.usage,
                        finishReason = result.finishReason,
                    )
                )
            )
        }
    }
}
