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

import io.github.autotweaker.api.base.session.getMessage
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.reflect.KProperty

@Serializable
@JvmInline
value class UserRef(@Serializable(with = UuidSerializer::class) val id: UUID)

operator fun UserRef?.getValue(thisRef: Any?, property: KProperty<*>) =
	this?.let { getMessage<AgentMessage.User>(it.id) }

@Serializable
@JvmInline
value class AssistantRef(@Serializable(with = UuidSerializer::class) val id: UUID)

operator fun AssistantRef?.getValue(thisRef: Any?, property: KProperty<*>) =
	this?.let { getMessage<AgentMessage.Assistant>(it.id) }

@Serializable
@JvmInline
value class ToolCallRef(@Serializable(with = UuidSerializer::class) val id: UUID)

operator fun ToolCallRef?.getValue(thisRef: Any?, property: KProperty<*>) =
	this?.let { getMessage<AgentMessage.Tool.Call>(it.id) }

@Serializable
@JvmInline
value class ToolResultRef(@Serializable(with = UuidSerializer::class) val id: UUID)

operator fun ToolResultRef?.getValue(thisRef: Any?, property: KProperty<*>) =
	this?.let { getMessage<AgentMessage.Tool.Result>(it.id) }

@Serializable
@JvmInline
value class CompactRef(@Serializable(with = UuidSerializer::class) val id: UUID)

operator fun CompactRef?.getValue(thisRef: Any?, property: KProperty<*>) =
	this?.let { getMessage<AgentMessage.Compact>(it.id) }
