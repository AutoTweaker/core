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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.*

object UsageStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val store by lazy { JsonStoreImpl.namespace(this::class) }
	private val mutex = Mutex()
	
	private val mapSerializer = MapSerializer(UuidSerializer, UsageSnapshot.serializer())
	
	private fun load(): Map<UUID, UsageSnapshot> {
		val raw = store.get() ?: return emptyMap()
		return Json.decodeFromJsonElement(mapSerializer, raw)
	}
	
	private fun save(data: Map<UUID, UsageSnapshot>) {
		store.set(Json.encodeToJsonElement(data))
	}
	
	suspend fun collect(messages: List<SessionMessage>) = mutex.withLock {
		val data = load().toMutableMap()
		var count = 0
		
		fun add(key: UUID, snapshot: UsageSnapshot) {
			if (key !in data) {
				data[key] = snapshot
				count++
			}
		}
		
		messages.forEach { message ->
			when (message) {
				is SessionMessage.Assistant -> message.usageSnapshot?.let {
					add(message.id, it)
				}
				
				is SessionMessage.Compact -> {
					message.snapshots?.forEach { (id, snapshot) ->
						add(id, snapshot)
					}
				}
				
				is SessionMessage.UsageRecord -> {
					add(message.id, message.snapshot)
				}
				
				else -> {}
			}
		}
		
		if (count > 0) {
			save(data)
			logger.info("Collected usage entries  new={}  total={}", count, data.size)
		}
	}
	
	suspend fun getSnapshots(): Map<UUID, UsageSnapshot> = mutex.withLock { load() }
}
