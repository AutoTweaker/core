package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.tool.Tool
import kotlin.time.Instant

@Suppress("unused")
sealed class AgentOutput {
	data class StreamMessage(
		val status: Status,
		val content: AgentChatStreamResult,
	) : AgentOutput() {
		enum class Status {
			RETRYING,
			REASONING,
			OUTPUTTING,
			FINISHED,
		}
	}
	
	//TODO 通过ContextUpdate，并增加单独的工具运行时输出通道
	data class ToolResult(
		val name: String,
		val callId: String,
		val content: String,
		val timestamp: Instant,
	) : AgentOutput()
	
	data class ToolCallRequest(
		val pendingToolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
	) : AgentOutput()
	
	data class ContextUpdate(
		val context: AgentContext,
		val reason: UpdateReason?,
	) : AgentOutput() {
		enum class UpdateReason {
			COMPACTED,
			LLM_ERROR,
		}
	}
	
	//TODO
	data class ToolListUpdate(
		val activeTools: List<Tool>,
	) : AgentOutput()
	
	data class Error(
		val message: String,
		val type: Type,
	) : AgentOutput() {
		enum class Type {
			NETWORK,
			LLM,
			TOOL,
			UNKNOWN
		}
	}
}
