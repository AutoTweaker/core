package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.llm.Usage
import io.github.autotweaker.core.tool.Tool
import kotlin.time.Instant

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
	
	//TODO 通过ContextUpdate，并增加单独的工具运行时输出通道
	@Suppress("unused")
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
			//TODO LLM_ERROR,
			//TODO ARCHIVED
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
