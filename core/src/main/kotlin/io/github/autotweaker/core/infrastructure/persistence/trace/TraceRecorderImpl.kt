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

package io.github.autotweaker.core.infrastructure.persistence.trace

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.trace.TraceRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes

object TraceRecorderImpl : Settable {
	private val scope = CoroutineScope(Dispatchers.IO)
	private val queue = Channel<TraceEntry>(Channel.UNLIMITED)
	private val cache = ConcurrentHashMap<KClass<*>, TraceRecorder>()
	
	fun init() {
		scope.launch {
			for (entry in queue) {
				TraceStore.insert(entry.origin, entry.namespace, entry.content)
			}
		}
		
		val interval = setting.get(TraceSettings.CleanupIntervalMinutes()).value
		if (interval > 0) scope.launch {
			while (isActive) {
				delay(interval.minutes)
				TraceCleanup.cleanup()
			}
		}
	}
	
	fun recorder(kClass: KClass<*>): TraceRecorder =
		cache.computeIfAbsent(kClass) { Recorder(it.java.name) }
	
	private class Recorder(private val origin: String) : TraceRecorder {
		override fun add(namespace: String, content: String) {
			queue.trySend(TraceEntry(origin, namespace, content))
		}
	}
	
	private data class TraceEntry(val origin: String, val namespace: String, val content: String)
}