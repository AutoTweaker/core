package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
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

fun agentChat(request: AgentChatRequest): Flow<AgentChatStreamResult> = flow {
	val stream = request.model.modelInfo.supportsStreaming
	val chatRequest = request.toChatRequest().copy(stream = stream)
	
	val results = resilientChat(
		model = request.model,
		fallbackModels = request.fallbackModels,
		request = chatRequest,
	)
	
	var reasoningContent = ""
	var content = ""
	var lastMessage: ChatMessage.AssistantMessage? = null
	var lastFinishReason: ChatResult.FinishReason? = null
	var lastUsage: Usage? = null
	var lastRetrying: Model? = null
	val errors = mutableListOf<AgentChatStreamResult.Failing.Error>()
	
	try {
		results.collect { resilientResult ->
			val result = resilientResult.result
			val msg = result.message
			
			if (resilientResult.retrying != null) {
				lastRetrying = resilientResult.retrying
			}
			
			if (msg is ChatMessage.ErrorMessage) {
				errors += AgentChatStreamResult.Failing.Error(
					content = msg.content,
					statusCode = msg.statusCode,
					retrying = resilientResult.retrying,
					timestamp = msg.createdAt,
				)
				emit(AgentChatStreamResult.Failing(errors = errors.toList()))
				return@collect
			}
			
			val assistantMsg = msg as? ChatMessage.AssistantMessage ?: return@collect
			lastMessage = assistantMsg
			
			if (result.finishReason == null) {
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
			}
			
			result.finishReason?.let { lastFinishReason = it }
			result.usage?.let { lastUsage = it }
		}
	} catch (_: IllegalStateException) {
		// 所有候选模型耗尽，之前已通过 Failing emit 错误
		return@flow
	}
	
	val msg = lastMessage
	val resultModel = lastRetrying ?: request.model
	
	emit(
		AgentChatStreamResult.Finished(
			result = AgentChatStreamResult.Finished.Result(
				context = AgentContext.Message.Assistant(
					reasoning = msg?.reasoningContent ?: reasoningContent.ifEmpty { null },
					content = msg?.content ?: content.ifEmpty { null },
					model = resultModel,
					timestamp = msg?.createdAt ?: Clock.System.now(),
				),
				toolCalls = toPendingToolCalls(msg?.toolCalls, msg?.createdAt ?: Clock.System.now(), resultModel),
				usage = lastUsage,
				finishReason = lastFinishReason,
			)
		)
	)
}
