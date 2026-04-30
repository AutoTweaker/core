package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import kotlin.time.Instant

data class AgentContext(
	val compactedRounds: List<CompactedRound>?,
	val systemPrompt: String?,
	val historyRounds: List<CompletedRound>?,
	val currentRound: CurrentRound?,
) {
	sealed class Message {
		data class User(
			//总结消息挂在前方
			val summarizedMessage: String?,
			val content: String?,
			val images: List<Base64>? = null,
			val timestamp: Instant,
		) : Message()
		
		data class Assistant(
			val reasoning: String? = null,
			val content: String? = null,
			val model: Model,
			val timestamp: Instant,
		) : Message()
		
		data class Tool(
			val name: String,
			val call: Call,
			val callId: String,
			val result: Result,
		) : Message() {
			data class Call(
				val arguments: String,
				val reason: String?,
				val timestamp: Instant,
				val model: Model,
			)
			
			data class Result(
				val content: String,
				val timestamp: Instant,
				val status: Status,
			) {
				enum class Status {
					SUCCESS,
					FAILURE,
					TIMEOUT,
					CANCELLED,
				}
			}
		}
	}
	
	data class CompactedRound(
		val compactedAt: Instant,
		val rounds: List<CompletedRound>,
		val incompleteRound: CurrentRound?,
	)
	
	data class CompletedRound(
		val userMessage: Message.User,
		val turns: List<Turn>?,
		val finalAssistantMessage: Message.Assistant?,
	)
	
	data class CurrentRound(
		val userMessage: Message.User,
		val turns: List<Turn>?,
		val assistantMessage: Message.Assistant? = null,
		val pendingToolCalls: List<PendingToolCall>? = null,
	) {
		data class PendingToolCall(
			val callId: String,
			val name: String,
			val model: Model,
			val arguments: String,
			val reason: String?,
			val timestamp: Instant,
		)
	}
	
	data class Turn(
		val assistantMessage: Message.Assistant,
		val tools: List<Message.Tool>,
	)
}