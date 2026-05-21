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

package io.github.autotweaker.api.types.session

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.serializer.InstantLongSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.time.Instant

@Serializable
sealed class SessionMessage {
	@Serializable(with = UuidSerializer::class)
	abstract val id: UUID
	
	@Serializable(with = InstantLongSerializer::class)
	abstract val timestamp: Instant
	
	@Serializable
	data class User(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		
		val content: String,
		val images: List<Base64>?
	) : SessionMessage()
	
	@Serializable
	data class Assistant(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		
		val reasoning: String?,
		val content: String?,
		@Serializable(with = UuidSerializer::class) val model: UUID,
	) : SessionMessage()
	
	@Serializable
	sealed class Tool : SessionMessage() {
		abstract val callId: String
		
		@Serializable
		data class Call(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			
			@Serializable(with = InstantLongSerializer::class)
			override val timestamp: Instant,
			
			override val callId: String,
			
			@Serializable(with = UuidSerializer::class)
			val assistantMessage: UUID,
			
			val name: String,
			val arguments: String,
			val reason: String?,
		) : Tool()
		
		@Serializable
		data class Result(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			
			@Serializable(with = InstantLongSerializer::class)
			override val timestamp: Instant,
			
			override val callId: String,
			val content: String,
			val status: ToolResultStatus
		) : Tool()
	}
	
	@Serializable
	data class Compact(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		
		val content: String,
	) : SessionMessage()
	
	@Serializable
	data class UsageRecord(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
	) : SessionMessage()
}
