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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.ChatMessage
import kotlin.time.Instant

fun MessageContent.inject(injectImageTag: Boolean = false): String = buildString {
	injections?.forEach { appendLine(it.toXml()) }
	if (injectImageTag) repeat(images?.size ?: 0) { appendLine("<image />") }
	content?.let { append(it) }
}

fun MessageContent.injectTimestamp(timestamp: Instant) = copy(
	injections = listOf(
		ContextInjection(
			tag = "timestamp",
			content = timestamp.toString()
		)
	)
			+ (injections ?: emptyList())
)

fun List<ChatMessage>.inject(injections: List<ContextInjection>?): List<ChatMessage> {
	if (injections.isNullOrEmpty()) return this
	val firstUserIndex = indexOfFirst { it is ChatMessage.UserMessage }
	if (firstUserIndex == -1) return this
	return mapIndexed { index, msg ->
		if (index == firstUserIndex) {
			val userMsg = msg as ChatMessage.UserMessage
			userMsg.copy(content = userMsg.content.inject(injections))
		} else msg
	}
}

fun String.inject(injections: List<ContextInjection>) = buildString {
	injections.forEach { appendLine(it.toXml()) }
	append(this@inject)
}

fun ContextInjection.toXml() = "<$tag>$content</$tag>"
