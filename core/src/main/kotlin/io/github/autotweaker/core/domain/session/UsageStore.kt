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
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

internal object UsageStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val store = JsonStoreImpl.namespace(this::class)
	
	private val mapSerializer = MapSerializer(String.serializer(), UsageSnapshot.serializer())
	
	private fun load(): MutableMap<String, UsageSnapshot> {
		val raw = store.get() ?: return mutableMapOf()
		return Json.decodeFromJsonElement(mapSerializer, raw).toMutableMap()
	}
	
	private fun save(data: Map<String, UsageSnapshot>) {
		store.set(Json.encodeToJsonElement(data))
	}
	
	fun collect(messages: List<SessionMessage>) = synchronized(this) {
		val data = load()
		var count = 0

		messages.forEach { message ->
			when (message) {
				is SessionMessage.Assistant -> message.usageSnapshot?.let { snapshot ->
					val key = message.id.toString()
					if (key !in data) {
						data[key] = snapshot
						count++
					}
				}

				is SessionMessage.Compact -> {
					message.snapshots?.forEachIndexed { index, snapshot ->
						val key = "${message.id}_$index"
						if (key !in data) {
							data[key] = snapshot
							count++
						}
					}
				}

				is SessionMessage.UsageRecord -> {
					val key = message.id.toString()
					if (key !in data) {
						data[key] = message.snapshot
						count++
					}
				}

				else -> {}
			}
		}

		if (count > 0) {
			save(data)
			logger.info("Collected usage entries  new={}  total={}", count, data.size)
		}
	}
	
	fun getSnapshots(): List<UsageSnapshot> = load().values.toList()
}
