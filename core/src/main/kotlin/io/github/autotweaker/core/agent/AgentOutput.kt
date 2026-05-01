package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.llm.Usage
import io.github.autotweaker.core.tool.Tool

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
	
	data class CompactOutput(
		val status: Status,
		val content: String,
		val usage: Usage?,
	) : AgentOutput() {
		enum class Status {
			OUTPUTTING,
			FINISHED,
			FAILED,
		}
	}
	
	data class ToolOutput(
		val name: String,
		val callId: String,
		val content: String,
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
			ARCHIVED,
			TOOL
		}
	}
	
	data class ToolListUpdate(
		val activeTools: List<Tool>,
	) : AgentOutput()
	
	data class Error(
		val message: String,
		val type: Type,
	) : AgentOutput() {
		enum class Type {
			LLM,
			COMPACT,
		}
	}
}
