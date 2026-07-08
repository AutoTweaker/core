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
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class MessageQueue(private val agentId: UUID) : Loggable {
	private val channel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val deliveries = ConcurrentHashMap<UUID, CompletableDeferred<UUID?>>()
	
	fun shutdown() {
		channel.close()
		deliveries.values.forEach { it.cancel() }
	}
	
	suspend fun receive(): RuntimeContext.Message.User {
		while (true) {
			val all = mutableMapOf<UUID, MessageContent>()
			//等一个
			all += channel.receive()
			//全拿完
			while (true) all += channel.tryReceive().getOrNull() ?: break
			
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
	
	fun drain(): RuntimeContext.Message.User? {
		val all = mutableMapOf<UUID, MessageContent>()
		while (true) all += channel.tryReceive().getOrNull() ?: break
		return merge(all)
	}
	
	fun merge(all: Map<UUID, MessageContent>): RuntimeContext.Message.User? {
		if (all.isEmpty()) return null
		val injections = all.values.flatMap { it.injections.orEmpty() }.orNull()
		val images = all.values.flatMap { it.images.orEmpty() }.orNull()
		val content = buildString {
			val filtered = all.values.filterNot { it.content.isNullOrBlank() }
			filtered.forEachBetween(
				action = { append(it.content) },
				between = { append("\n\n---\n\n") })
		}.orNull()
		if (allNull(injections, images, content)) {
			all.keys.forEach {
				deliveries.remove(it)?.complete(null)
			}
			return null
		}
		return RuntimeContext.Message.User(
			content = MessageContent(
				injections, content, images
			),
			timestamp = Clock.System.now()
		).also { message ->
			all.keys.forEach {
				deliveries.remove(it)?.complete(message.id)
			}
		}.andLog(log) { info("Merged queued messages  count={}  agentId={}", all.count(), agentId) }
	}
	
	fun send(content: List<String>) = content.map {
		send(it)
	}
	
	fun send(content: String) = send(
		MessageContent(content = content)
	)
	
	fun send(injection: ContextInjection) = send(
		MessageContent(injections = listOf(injection))
	)
	
	fun send(msg: MessageContent): Delivery {
		val token = UUID.randomUUID()
		val deferred = CompletableDeferred<UUID?>()
		deliveries[token] = deferred
		channel.trySend(token to msg)
		return object : Delivery {
			override suspend fun await() = deferred.await()
		}
	}
}
