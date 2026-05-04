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

package io.github.autotweaker.core.session

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.Usage
import java.util.*
import kotlin.time.Instant

data class SessionContext(
	val systemPrompt: String,
	val messages: List<SessionMessage>,
	val usage: Map<UUID, Usage>,
) {
	sealed class SessionMessage {
		abstract val id: UUID
		abstract val timestamp: Instant
		abstract val inContext: Boolean
		
		data class User(
			override val id: UUID,
			override val timestamp: Instant,
			override val inContext: Boolean,
			val content: String,
			val images: List<Base64>
		) : SessionMessage()
		
		data class Assistant(
			override val id: UUID,
			override val timestamp: Instant,
			override val inContext: Boolean,
			val reasoning: String,
			val content: String,
			val model: ModelId,
		) : SessionMessage()
		
		sealed class Tool : SessionMessage() {
			abstract val callId: String
			abstract val name: String
			
			data class Call(
				override val id: UUID,
				override val timestamp: Instant,
				override val inContext: Boolean,
				override val callId: String,
				override val name: String,
				val arguments: String,
			) : Tool()
			
			data class Result(
				override val id: UUID,
				override val timestamp: Instant,
				override val inContext: Boolean,
				override val callId: String,
				override val name: String,
				val content: String,
				val status: AgentContext.Message.Tool.Result.Status
			) : Tool()
		}
		
		@Suppress("unused")
		data class Compact(
			override val id: UUID,
			override val timestamp: Instant,
			override val inContext: Boolean,
			val content: String,
		) : SessionMessage()
	}
}