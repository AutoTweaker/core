/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.tool.ToolResultStatus
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

data class AgentContext(
	val compactedRounds: List<CompactedRound>?,
	val systemPrompt: String?,
	val injections: List<ContextInjection>?,
	val historyRounds: List<CompletedRound>?,
	val summarizedMessage: SummarizedMessage?,
	val currentRound: CurrentRound?,
) {
	data class SummarizedMessage(
		val id: UUID = UUID.randomUUID(),
		val timestamp: Instant,
		val content: String,
		val snapshots: Map<UUID, UsageSnapshot>? = null,
	)
	
	sealed class Message {
		data class User(
			val id: UUID = UUID.randomUUID(),
			val content: MessageContent,
			val timestamp: Instant,
		) : Message()
		
		data class Assistant(
			val id: UUID = UUID.randomUUID(),
			val reasoning: String? = null,
			val content: String? = null,
			val modelId: UUID,
			val timestamp: Instant,
			val usageSnapshot: UsageSnapshot? = null,
		) : Message()
		
		data class Tool(
			val name: String,
			val call: Call,
			val callId: String,
			val result: Result,
		) : Message() {
			data class Call(
				val id: UUID = UUID.randomUUID(),
				val assistantMessageId: UUID,
				val arguments: String,
				val reason: String? = null,
				val timestamp: Instant,
				val validatedArgs: JsonElement? = null,
			)
			
			data class Result(
				val id: UUID = UUID.randomUUID(),
				val content: String,
				val timestamp: Instant,
				val status: ToolResultStatus,
			)
		}
	}
	
	data class CompactedRound(
		val rounds: List<CompletedRound>,
		val summarizedMessage: SummarizedMessage
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
			val id: UUID = UUID.randomUUID(),
			val callId: String,
			val name: String,
			val arguments: String,
			val reason: String,
			val timestamp: Instant,
			val validatedArgs: JsonElement,
		)
	}
	
	data class Turn(
		val assistantMessage: Message.Assistant,
		val tools: List<Message.Tool>,
	)
}
