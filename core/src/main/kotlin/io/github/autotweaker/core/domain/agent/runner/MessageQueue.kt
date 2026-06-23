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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.core.domain.agent.AgentContext
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.time.Clock

class MessageQueue(private val agentId: UUID) : Loggable {
	private val channel = Channel<MessageContent>(Channel.UNLIMITED)
	
	suspend fun receive(): AgentContext.Message.User {
		while (true) {
			val all = mutableListOf<MessageContent>()
			//等一个
			all.add(channel.receive())
			//全拿完
			while (true) all.add(channel.tryReceive().getOrNull() ?: break)
			
			merge(all)?.let {
				return it.andLog(log) { message ->
					info(
						"Received message  injections={}  images={}  length={}  agentId={}",
						message.content.injections?.count(),
						message.content.images?.count(),
						message.content.content?.length,
						agentId
					)
				}
			}
		}
	}
	
	fun drain(): AgentContext.Message.User? {
		val all = mutableListOf<MessageContent>()
		while (true) all.add(channel.tryReceive().getOrNull() ?: break)
		return merge(all)
	}
	
	fun merge(all: List<MessageContent>): AgentContext.Message.User? {
		if (all.isEmpty()) return null
		val injections = all.flatMap { it.injections.orEmpty() }.orNull()
		val images = all.flatMap { it.images.orEmpty() }.orNull()
		val content = buildString {
			val filtered = all.filterNot { it.content.isNullOrBlank() }
			filtered.forEachBetween(
				action = { append(it.content) },
				between = { append("\n\n---\n\n") })
		}.orNull()
		if (allNull(injections, images, content)) return null
		return AgentContext.Message.User(
			content = MessageContent(
				injections, content, images
			),
			timestamp = Clock.System.now()
		).andLog(log) { info("Merged queued messages  count={}  agentId={}", all.count(), agentId) }
	}
	
	fun sendReasons(reasons: List<String>) = reasons.forEach {
		send(it)
	}
	
	fun send(content: String) = send(
		MessageContent(content = content)
	)
	
	fun send(injection: ContextInjection) = send(
		MessageContent(injections = listOf(injection))
	)
	
	fun send(msg: MessageContent) = channel.trySend(msg).discard()
}
