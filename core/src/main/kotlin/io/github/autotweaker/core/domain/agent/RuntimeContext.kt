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
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

data class RuntimeContext(
	val systemPrompt: String?,
	val injections: List<ContextInjection>?,
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<CompletedRound>?,
	val currentRound: CurrentRound?,
) {
	data class SummarizedMessage(
		val id: UUID,
		val timestamp: Instant,
		val content: String,
		val modelId: UUID,
		val usage: Usage?,
	)
	
	sealed class Message {
		data class User(
			val id: UUID,
			val content: MessageContent,
			val timestamp: Instant,
		) : Message()
		
		data class Assistant(
			val id: UUID,
			val reasoning: String?,
			val content: String?,
			val modelId: UUID,
			val timestamp: Instant,
			val usage: Usage?,
		) : Message()
		
		data class Tool(
			val call: Call,
			val callId: String,
			val result: Result,
		) : Message() {
			data class Call(
				val id: UUID,
				val timestamp: Instant,
				val callName: String,
				val arguments: String,
				val reason: String?,
				val validatedToolName: String?,
				val validatedArgs: JsonElement?,
				val resolvedRequest: JsonElement?,
				val presentation: ToolPresentation?,
			)
			
			data class Result(
				val id: UUID,
				val content: String,
				val data: JsonElement?,
				val presentation: ToolPresentation,
				val timestamp: Instant,
				val status: ToolResultStatus,
			)
		}
	}
	
	data class CompactedRounds(
		val compactedRounds: CompactedRounds?,
		val rounds: List<CompletedRound>,
		val summarizedMessage: SummarizedMessage
	) {
		fun completedRounds(): List<CompletedRound> = compactedRounds?.completedRounds().orEmpty() + rounds
		
		fun forEach(block: (CompactedRounds) -> Unit) {
			compactedRounds?.forEach(block)
			block(this)
		}
	}
	
	data class CompletedRound(
		val userMessage: Message.User,
		val turns: List<Turn>?,
		val finalAssistantMessage: Message.Assistant?,
	)
	
	data class CurrentRound(
		val userMessage: Message.User,
		val turns: List<Turn>?,
		val assistantMessage: Message.Assistant?,
		val pendingToolCalls: List<PendingToolCall>?,
	) {
		data class PendingToolCall(
			val id: UUID,
			val timestamp: Instant,
			val callId: String,
			val callName: String,
			val arguments: String,
			val reason: String,
			val validatedToolName: String,
			val validatedArgs: JsonElement,
			val resolvedRequest: JsonElement,
			val presentation: ToolPresentation,
		)
	}
	
	data class Turn(
		val assistantMessage: Message.Assistant,
		val tools: List<Message.Tool>,
	)
}
