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

@file:Suppress("UNCHECKED_CAST")

package io.github.autotweaker.api.base.session

import io.github.autotweaker.api.ServiceRegistry
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.AgentMessageType
import io.github.autotweaker.api.types.message.type
import java.util.*

inline fun <reified T : AgentMessage> getMessage(id: UUID): MessageResult<T> {
	val msg = ServiceRegistry.get().message.get(id)
	return if (msg is T) MessageResult.of(msg)
	else MessageResult.Companion.of(
		CorruptedMessage(
			id = id,
			expected = T::class.type(),
			actual = msg?.type()
		)
	)
}

@JvmInline
value class MessageResult<out T : AgentMessage> private constructor(
	@PublishedApi
	internal val value: Any
) {
	val isIntact get() = value !is CorruptedMessage
	val isCorrupted get() = value is CorruptedMessage
	fun getOrNull() = if (isCorrupted) null else value as T
	fun corruptedOrNull() = value as? CorruptedMessage
	
	inline fun onIntact(action: (T) -> Unit): MessageResult<T> = also {
		if (isIntact) action(value as T)
	}
	
	inline fun onCorrupted(action: (CorruptedMessage) -> Unit): MessageResult<T> = also {
		corruptedOrNull()?.let { action(it) }
	}
	
	inline fun <R> fold(onIntact: (T) -> R, onCorrupted: (CorruptedMessage) -> R): R {
		val corrupted = corruptedOrNull()
		return if (corrupted == null) onIntact(value as T)
		else onCorrupted(corrupted)
	}
	
	companion object {
		fun <T : AgentMessage> of(message: T) = MessageResult<T>(message)
		fun <T : AgentMessage> of(corrupted: CorruptedMessage) = MessageResult<T>(corrupted)
	}
}

data class CorruptedMessage(
	val id: UUID,
	val expected: AgentMessageType,
	val actual: AgentMessageType?,
)
