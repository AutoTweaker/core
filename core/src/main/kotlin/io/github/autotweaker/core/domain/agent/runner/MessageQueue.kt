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
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.textPart
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class MessageQueue(private val agentId: UUID) : Loggable {
	private val channel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val deliveries = ConcurrentHashMap<UUID, CompletableDeferred<UUID?>>()
	
	private val cancelled = mutableSetOf<UUID>()
	private val lock = ReentrantMutex()
	
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
						"Received message  injections={}  contents={}  agentId={}",
						message.content.injections?.count(),
						message.content.content?.count(),
						agentId
					)
				}
			}
		}
	}
	
	suspend fun drain(): RuntimeContext.Message.User? {
		val all = mutableMapOf<UUID, MessageContent>()
		while (true) all += channel.tryReceive().getOrNull() ?: break
		return merge(all)
	}
	
	suspend fun merge(all: Map<UUID, MessageContent>): RuntimeContext.Message.User? {
		if (all.isEmpty()) return null
		val cancelQueued = lock.withLock {
			cancelled.toSet().also { cancelled.clear() }
		}
		val filtered = all.filterNot { it.key in cancelQueued }
		val injections = filtered.values.flatMap { it.injections.orEmpty() }.orNull()
		val content = buildList {
			filtered.values.forEach { msg ->
				msg.content?.let { addAll(it) }
			}
		}.orNull()
		if (allNull(injections, content)) {
			filtered.keys.forEach {
				deliveries.remove(it)?.complete(null)
			}
			return null
		}
		return RuntimeContext.Message.User(
			id = UUID(),
			content = MessageContent(
				injections, content
			),
			timestamp = Clock.System.now()
		).also { message ->
			filtered.keys.forEach {
				deliveries.remove(it)?.complete(message.id)
			}
		}.andLog(log) { info("Merged queued messages  count={}  agentId={}", filtered.count(), agentId) }
	}
	
	fun send(content: List<String>) = content.map {
		send(it)
	}
	
	fun send(content: String) = send(
		MessageContent(content = content.textPart())
	)
	
	fun send(injection: ContextInjection) = send(
		MessageContent(injections = listOf(injection))
	)
	
	fun send(msg: MessageContent): Delivery {
		val token = UUID()
		val deferred = CompletableDeferred<UUID?>()
		deliveries[token] = deferred
		channel.trySend(token to msg)
		return object : Delivery {
			override val isActive get() = deferred.isActive
			override suspend fun await() = deferred.await()
			override suspend fun cancel() = lock.withLock {
				deferred.cancel()
				cancelled.add(token)
			}.discard()
		}
	}
}
