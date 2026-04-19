package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.agent.llm.AgentContext
import io.github.autotweaker.core.agent.llm.agentChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow

sealed class StreamProcessResult {
	data object Completed : StreamProcessResult()
	data class ToolCallsRequired(
		val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
	) : StreamProcessResult()
	
	data object Cancelled : StreamProcessResult()
	data class Failed(val message: String) : StreamProcessResult()
}

class AgentStreamProcessor(
	private val output: MutableSharedFlow<AgentOutput>,
	private val onStatusChange: (AgentStatus) -> Unit,
	private val onContextUpdate: (AgentContext) -> Unit,
) {
	suspend fun process(
		request: AgentChatRequest,
		currentContext: AgentContext,
	): StreamProcessResult {
		var earlyResult: StreamProcessResult? = null
		
		try {
			agentChat(request).collect { result ->
				when (result) {
					is AgentChatStreamResult.Reasoning -> {
						output.emit(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.REASONING, result))
					}
					
					is AgentChatStreamResult.Outputting -> {
						output.emit(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.OUTPUTTING, result))
					}
					
					is AgentChatStreamResult.Failing -> {
						val retrying = result.errors.lastOrNull()?.retrying
						if (retrying != null) {
							onStatusChange(AgentStatus.RETRYING)
							output.emit(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.RETRYING, result))
						} else {
							val errorMessage = result.errors.lastOrNull()?.content ?: "All retries exhausted"
							output.emit(AgentOutput.Error(errorMessage, AgentOutput.Error.Type.LLM))
							earlyResult = StreamProcessResult.Failed(errorMessage)
							return@collect
						}
					}
					
					is AgentChatStreamResult.Finished -> {
						val updatedRound = currentContext.currentRound?.copy(
							assistantMessage = result.result.context,
							pendingToolCalls = result.result.toolCalls,
						)
						onContextUpdate(currentContext.copy(currentRound = updatedRound))
						
						output.emit(
							AgentOutput.StreamMessage(
								AgentOutput.StreamMessage.Status.FINISHED,
								result,
							)
						)
						
						if (!result.result.toolCalls.isNullOrEmpty()) {
							output.emit(AgentOutput.ToolCallRequest(result.result.toolCalls))
							earlyResult = StreamProcessResult.ToolCallsRequired(result.result.toolCalls)
						} else {
							earlyResult = StreamProcessResult.Completed
						}
						return@collect
					}
				}
			}
			return earlyResult ?: StreamProcessResult.Completed
		} catch (_: CancellationException) {
			return StreamProcessResult.Cancelled
		} catch (e: Exception) {
			val message = buildString {
				append(e::class.simpleName ?: e::class.qualifiedName ?: "UnknownException")
				e.message?.let { append(": ").append(it) }
				val cause = e.cause
				if (cause != null) append(" (caused by ").append(
					cause::class.simpleName ?: cause::class.qualifiedName
				).append(")")
			}
			output.emit(AgentOutput.Error(message, AgentOutput.Error.Type.LLM))
			return StreamProcessResult.Failed(message)
		}
	}
}
