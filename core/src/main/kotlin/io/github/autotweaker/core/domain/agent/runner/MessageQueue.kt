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
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class MessageQueue(private val agentId: UUID) : Loggable {
	private val channel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val coalescingChannel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val deliveries = ConcurrentHashMap<UUID, CompletableDeferred<Pair<UUID, MessageContent>?>>()
	
	private val cancelled = mutableSetOf<UUID>()
	private val lock = ReentrantMutex()
	
	@OptIn(ExperimentalCoroutinesApi::class)
	fun isEmpty() = channel.isEmpty && coalescingChannel.isEmpty
	
	fun shutdown() {
		channel.close()
		coalescingChannel.close()
		deliveries.values.forEach { it.cancel() }
	}
	
	suspend fun receive(): RuntimeContext.Message.User {
		while (true) {
			val all = mutableMapOf<UUID, MessageContent>()
			
			while (true) {
				all += channel.receive()
				val cancelQueued = lock.withLock { cancelled.toSet() }
				if (all.all { it.key in cancelQueued }) {
					all.clear()
				} else break
			}
			
			drainInto(all)
			
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
	
	suspend fun drainPrimary(): RuntimeContext.Message.User? {
		val all = mutableMapOf<UUID, MessageContent>()
		all += channel.tryReceive().getOrNull() ?: return null
		while (true) {
			val cancelQueued = lock.withLock { cancelled.toSet() }
			if (all.all { it.key in cancelQueued }) {
				all.clear()
				all += channel.tryReceive().getOrNull() ?: return null
			} else break
		}
		drainInto(all)
		return merge(all)
	}
	
	suspend fun drainAll(): RuntimeContext.Message.User? {
		val all = mutableMapOf<UUID, MessageContent>()
		drainInto(all)
		return merge(all)
	}
	
	private fun drainInto(all: MutableMap<UUID, MessageContent>) {
		while (true) all += channel.tryReceive().getOrNull() ?: break
		while (true) all += coalescingChannel.tryReceive().getOrNull() ?: break
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
				deliveries.remove(it)?.complete(message.id to message.content)
			}
		}.andLog(log) { info("Merged queued messages  count={}  agentId={}", filtered.count(), agentId) }
	}
	
	fun send(content: List<String>) = content.map {
		send(it)
	}
	
	fun send(content: String) = send(
		MessageContent(content = content.toContentPart())
	)
	
	fun send(injection: ContextInjection) = send(
		MessageContent(injections = listOf(injection))
	)
	
	fun send(msg: MessageContent) = send(msg, channel)
	
	fun sendCoalescing(msg: MessageContent) = send(msg, coalescingChannel)
	
	private fun send(msg: MessageContent, channel: SendChannel<Pair<UUID, MessageContent>>): Delivery {
		val token = UUID()
		val deferred = CompletableDeferred<Pair<UUID, MessageContent>?>()
		deliveries[token] = deferred
		channel.trySend(token to msg)
		return object : Delivery {
			override val isActive get() = deferred.isActive
			override suspend fun await() = deferred.await()
			override suspend fun cancel() = lock.withLock {
				deferred.cancel()
				deliveries.remove(token)
				cancelled.add(token)
			}.discard()
		}
	}
}
