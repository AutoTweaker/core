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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.agent

import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentMessage
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0MessageContent
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0Usage
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.tool.V0ToolResultStatus
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.tool.V0UiBlock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

@Serializable
sealed class V1AgentMessage {
	@Serializable(with = UuidSerializer::class)
	abstract val id: UUID
	abstract val timestamp: Instant
	abstract val origin: Set<@Serializable(with = UuidSerializer::class) UUID>
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.User")
	data class User(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		override val timestamp: Instant,
		override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
		val content: V0MessageContent,
	) : V1AgentMessage()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.Assistant")
	data class Assistant(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		override val timestamp: Instant,
		override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
		val reasoning: String?,
		val content: String?,
		@Serializable(with = UuidSerializer::class)
		val model: UUID,
		val usage: V0Usage?,
	) : V1AgentMessage()
	
	@Serializable
	sealed class Tool : V1AgentMessage() {
		abstract val callId: String
		
		@Serializable
		@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.Tool.Call")
		data class Call(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			override val timestamp: Instant,
			override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
			override val callId: String,
			val callName: String,
			val arguments: String,
			val reason: String?,
			val validatedToolName: String?,
			val validatedArgs: JsonElement?,
			val resolvedRequest: JsonElement?,
			val presentation: List<V0UiBlock>?,
		) : Tool()
		
		@Serializable
		@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.Tool.Result")
		data class Result(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			override val timestamp: Instant,
			override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
			override val callId: String,
			val content: String,
			val data: JsonElement?,
			val presentation: List<V0UiBlock>,
			val status: V0ToolResultStatus
		) : Tool()
	}
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.Compact")
	data class Compact(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		override val timestamp: Instant,
		override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
		val content: String,
		@Serializable(with = UuidSerializer::class)
		val model: UUID,
		val usage: V0Usage?,
	) : V1AgentMessage()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.agent.AgentMessage.UsageRecord")
	data class UsageRecord(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		override val timestamp: Instant,
		override val origin: Set<@Serializable(with = UuidSerializer::class) UUID>,
		@Serializable(with = UuidSerializer::class)
		val model: UUID,
		val usage: V0Usage,
	) : V1AgentMessage()
}

fun V0AgentMessage.toV1(origin: Set<UUID>): V1AgentMessage = when (this) {
	is V0AgentMessage.User -> V1AgentMessage.User(
		id = id, timestamp = timestamp, origin = origin, content = content
	)
	
	is V0AgentMessage.Assistant -> V1AgentMessage.Assistant(
		id = id, timestamp = timestamp, origin = origin,
		reasoning = reasoning, content = content, model = model, usage = usage
	)
	
	is V0AgentMessage.Tool.Call -> V1AgentMessage.Tool.Call(
		id = id, timestamp = timestamp, origin = origin, callId = callId,
		callName = callName, arguments = arguments, reason = reason,
		validatedToolName = validatedToolName, validatedArgs = validatedArgs,
		resolvedRequest = resolvedRequest, presentation = presentation
	)
	
	is V0AgentMessage.Tool.Result -> V1AgentMessage.Tool.Result(
		id = id, timestamp = timestamp, origin = origin, callId = callId,
		content = content, data = data, presentation = presentation, status = status
	)
	
	is V0AgentMessage.Compact -> V1AgentMessage.Compact(
		id = id, timestamp = timestamp, origin = origin,
		content = content, model = model, usage = usage
	)
	
	is V0AgentMessage.UsageRecord -> V1AgentMessage.UsageRecord(
		id = id, timestamp = timestamp, origin = origin, model = model, usage = usage
	)
}

fun typeOfV1(message: V1AgentMessage): V1AgentMessageType = when (message) {
	is V1AgentMessage.User -> V1AgentMessageType.USER
	is V1AgentMessage.Assistant -> V1AgentMessageType.ASSISTANT
	is V1AgentMessage.Tool.Call -> V1AgentMessageType.TOOL_CALL
	is V1AgentMessage.Tool.Result -> V1AgentMessageType.TOOL_RESULT
	is V1AgentMessage.Compact -> V1AgentMessageType.COMPACT
	is V1AgentMessage.UsageRecord -> V1AgentMessageType.USAGE_RECORD
}
