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

package io.github.autotweaker.api.types.message

import kotlin.reflect.KClass

enum class AgentMessageType { USER, ASSISTANT, TOOL_CALL, TOOL_RESULT, COMPACT, USAGE_RECORD }

fun AgentMessage.type() = when (this) {
	is AgentMessage.User -> AgentMessageType.USER
	is AgentMessage.Assistant -> AgentMessageType.ASSISTANT
	is AgentMessage.Tool.Call -> AgentMessageType.TOOL_CALL
	is AgentMessage.Tool.Result -> AgentMessageType.TOOL_RESULT
	is AgentMessage.Compact -> AgentMessageType.COMPACT
	is AgentMessage.UsageRecord -> AgentMessageType.USAGE_RECORD
}

fun KClass<out AgentMessage>.type() = when (this) {
	AgentMessage.User::class -> AgentMessageType.USER
	AgentMessage.Assistant::class -> AgentMessageType.ASSISTANT
	AgentMessage.Tool.Call::class -> AgentMessageType.TOOL_CALL
	AgentMessage.Tool.Result::class -> AgentMessageType.TOOL_RESULT
	AgentMessage.Compact::class -> AgentMessageType.COMPACT
	AgentMessage.UsageRecord::class -> AgentMessageType.USAGE_RECORD
	else -> error("Unknown AgentMessage type: $this")
}
