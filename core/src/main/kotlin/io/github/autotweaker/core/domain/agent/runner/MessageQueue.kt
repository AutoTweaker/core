/*
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
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.ContextInjection
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.toContentPart
import io.github.autotweaker.core.domain.agent.MessageBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MessageQueue(private val agentId: UUID, private val msg: MessageBuilder) : Loggable {
	private val channel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val coalescingChannel = Channel<Pair<UUID, MessageContent>>(Channel.UNLIMITED)
	private val deliveries = ConcurrentHashMap<UUID, CompletableDeferred<AgentMessage.User?>>()
	
	private val cancelled = mutableSetOf<UUID>()
	private var merging: Set<UUID>? = null
	
	private val lock = ReentrantMutex()
	
	fun isEmpty() = deliveries.isEmpty()
	
	fun shutdown() {
		channel.close()
		coalescingChannel.close()
		deliveries.values.forEach { it.cancel() }
	}
	
	fun failed(exception: Throwable) {
		channel.close(exception)
		coalescingChannel.close(exception)
		deliveries.values.forEach {
			it.completeExceptionally(exception)
		}
	}
	
	suspend fun receive(): AgentMessage.User {
		while (true) {
			var first: Pair<UUID, MessageContent>
			while (true) {
				first = channel.receive() // 取一条，可能挂起
				val cancelled = lock.withLock { first.first in cancelled } // 这条是否已经取消
				if (!cancelled) break // 这条没有取消，有效
				// 这条取消了，回到开头重取
			}
			
			val all = mutableMapOf(first)
			val message = drainInto(all) ?: continue // 消息都是空的，或者都取消了，重来
			log.info(
				"Received message  injections={}  contents={}  agentId={}",
				message.content.injections?.count(),
				message.content.content?.count(),
				agentId
			)
			return message
		}
	}
	
	suspend fun drainPrimary(): AgentMessage.User? {
		var first: Pair<UUID, MessageContent>
		while (true) {
			first = channel.tryReceive().getOrNull() ?: return null // 空的，return
			val cancelled = lock.withLock { first.first in cancelled }
			if (!cancelled) break
		}
		val all = mutableMapOf(first)
		return drainInto(all)
	}
	
	suspend fun drainAll(): AgentMessage.User? = drainInto(mutableMapOf())
	
	private suspend fun drainInto(all: MutableMap<UUID, MessageContent>): AgentMessage.User? {
		while (true) all += channel.tryReceive().getOrNull() ?: break
		while (true) all += coalescingChannel.tryReceive().getOrNull() ?: break
		return merge(all)
	}
	
	suspend fun merge(all: Map<UUID, MessageContent>): AgentMessage.User? {
		check(merging == null) { "merging must be null" }
		if (all.isEmpty()) return null
		val cancelQueued: Set<UUID>
		val filtered: Map<UUID, MessageContent>
		lock.withLock {
			cancelQueued = cancelled.toSet()
			cancelled.clear()
			filtered = all.filterNot { it.key in cancelQueued }
			merging = filtered.keys.orNull()
		}
		if (filtered.isEmpty()) return null
		val injections = filtered.values.flatMap { it.injections.orEmpty() }.orNull()
		val content = buildList {
			filtered.values.forEach { msg ->
				msg.content?.let { addAll(it) }
			}
		}.orNull()
		if (allNull(injections, content)) {
			completeAll(null)
			return null
		}
		return msg.user(MessageContent(injections, content)).andLog(log) {
			info("Merged queued messages  count={}  agentId={}", filtered.count(), agentId)
		}
	}
	
	suspend fun completeAll(msg: AgentMessage.User?) = lock.withLock {
		val ids = checkNotNull(merging) { "merging is null" }
		merging = null
		ids.forEach {
			deliveries.remove(it)?.complete(msg)
		}
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
		val deferred = CompletableDeferred<AgentMessage.User?>()
		deliveries[token] = deferred
		channel.trySend(token to msg) // channel是UNLIMITED，send只会在close时失败，相比之下trySend不用suspend
		return object : Delivery {
			override val isActive get() = deferred.isActive
			override val isCompleted get() = deferred.isCompleted
			override val isCancelled get() = deferred.isCancelled
			override suspend fun await() = deferred.await()
			override suspend fun cancel() = lock.withLock {
				if (deferred.isCancelled) return@withLock true
				if (deferred.isCompleted) return@withLock false
				val merging = merging
				if (merging != null && token in merging) return@withLock false
				deferred.cancel()
				deliveries.remove(token)
				cancelled.add(token)
				return@withLock true
			}
		}
	}
}
