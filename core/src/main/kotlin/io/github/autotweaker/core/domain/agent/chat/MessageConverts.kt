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
import io.github.autotweaker.api.types.llm.ContentPart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.*
import kotlin.time.Instant

fun MessageContent.inject() = content.inject(injections)

@JvmName("injectContentParts")
fun List<ContentPart>?.inject(
	injections: List<ContextInjection>?
): List<ContentPart> = buildList {
	injections?.forEach { add(ContentPart.Text(it.toXml())) }
	this@inject?.let { addAll(it) }
}

fun List<ContentPart>.merge(): String = buildString {
	this@merge.forEach {
		if (it is ContentPart.Text) appendLine(it.content)
		else appendLine("<media />")
	}
}

fun MessageContent.injectContext(
	timestamp: Instant,
	timeZone: TimeZone,
	language: Locale,
) = copy(
	injections = listOf(
		ContextInjection(
			"utc_time", timestamp
		), ContextInjection(
			"local_time", timestamp.toLocalDateTime(timeZone)
		), ContextInjection(
			"timezone", timeZone
		), ContextInjection(
			"language", language
		)
	) + injections.orEmpty()
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

@JvmName("injectChatMessages")
fun List<ChatMessage>.inject(injections: List<ContextInjection>?): List<ChatMessage> {
	if (injections.isNullOrEmpty()) return this
	val firstUserIndex = indexOfFirst { it is ChatMessage.User }
	if (firstUserIndex == -1) return this
	val mutable = toMutableList()
	val userMsg = mutable[firstUserIndex] as ChatMessage.User
	mutable[firstUserIndex] = userMsg.copy(content = userMsg.content.inject(injections))
	return mutable
}

fun ContextInjection.toXml() = "<$tag>$content</$tag>"
