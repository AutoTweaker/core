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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.*
import kotlin.time.Instant

fun MessageContent.inject(injectImageTag: Boolean = false): String = buildString {
	injections?.forEach { appendLine(it.toXml()) }
	if (injectImageTag) repeat(images?.size ?: 0) { appendLine("<image />") }
	content?.let { append(it) }
}

fun MessageContent.injectContext(
	timestamp: Instant,
	timeZone: TimeZone,
	language: Locale,
) = copy(
	injections = injections.orEmpty() + ContextInjection(
		"utc_time", timestamp
	) + ContextInjection(
		"local_time", timestamp.toLocalDateTime(timeZone)
	) + ContextInjection(
		"timezone", timeZone
	) + ContextInjection(
		"language", language
	)
)

fun List<ChatMessage>.inject(
	injections: List<ContextInjection>?, summarize: String?
): List<ChatMessage> = inject(buildList {
	summarize?.let {
		add(
			ContextInjection(
				"summary",
				summarize
			)
		)
	}
	injections?.let {
		addAll(it)
	}
})

fun List<ChatMessage>.inject(injections: List<ContextInjection>?): List<ChatMessage> {
	if (injections.isNullOrEmpty()) return this
	val firstUserIndex = indexOfFirst { it is ChatMessage.UserMessage }
	if (firstUserIndex == -1) return this
	val mutable = toMutableList()
	val userMsg = mutable[firstUserIndex] as ChatMessage.UserMessage
	mutable[firstUserIndex] = userMsg.copy(content = userMsg.content.inject(injections))
	return mutable
}

fun String.inject(injections: List<ContextInjection>) = buildString {
	injections.forEach { appendLine(it.toXml()) }
	append(this@inject)
}

fun ContextInjection.toXml() = "<$tag>$content</$tag>"
