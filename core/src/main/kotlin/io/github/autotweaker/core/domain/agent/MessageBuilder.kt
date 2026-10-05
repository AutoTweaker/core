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

import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.now
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.infrastructure.persist.db.session.MessageCacheImpl
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

class MessageBuilder(
	private val agentId: UUID,
	private val messageCacheImpl: MessageCacheImpl
) {
	suspend fun user(
		content: MessageContent
	) = AgentMessage.User(
		id = UUID(),
		timestamp = now(),
		origin = agentId,
		content = content
	).save()
	
	suspend fun assistant(
		reasoning: String?,
		content: String?,
		model: UUID,
		usage: Usage?
	) = AgentMessage.Assistant(
		id = UUID(),
		timestamp = now(),
		origin = agentId,
		reasoning = reasoning,
		content = content,
		model = model,
		usage = usage
	).save()
	
	suspend fun toolCall(
		timestamp: Instant,
		callId: String,
		callName: String,
		arguments: String,
		reason: String?,
		validatedToolName: String?,
		validatedArgs: JsonElement?,
		resolvedRequest: JsonElement?,
		presentation: ToolPresentation?,
	) = AgentMessage.Tool.Call(
		id = UUID(),
		timestamp = timestamp,
		origin = agentId,
		callId = callId,
		callName = callName,
		arguments = arguments,
		reason = reason,
		validatedToolName = validatedToolName,
		validatedArgs = validatedArgs,
		resolvedRequest = resolvedRequest,
		presentation = presentation
	).save()
	
	suspend fun toolResult(
		callId: String,
		content: String,
		data: JsonElement?,
		presentation: ToolPresentation,
		status: ToolResultStatus
	) = AgentMessage.Tool.Result(
		id = UUID(),
		timestamp = now(),
		origin = agentId,
		callId = callId,
		content = content,
		data = data,
		presentation = presentation,
		status = status
	).save()
	
	suspend fun compact(
		content: String,
		model: UUID,
		usage: Usage?,
	) = AgentMessage.Compact(
		id = UUID(),
		timestamp = now(),
		origin = agentId,
		content = content,
		model = model,
		usage = usage
	).save()
	
	private suspend fun <T : AgentMessage> T.save() = also { messageCacheImpl.save(this) }
}
