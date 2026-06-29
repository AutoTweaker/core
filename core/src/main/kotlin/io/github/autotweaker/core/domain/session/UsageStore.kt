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

import io.github.autotweaker.api.JsonStorable
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Mutable.Companion.mutable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.store
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.SessionMessage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

object UsageStore : Loggable, JsonStorable {
	private val cache by lazy {
		store.get()?.let {
			Json.decodeFromJsonElement(
				deserializer = MapSerializer(
					keySerializer = UuidSerializer, valueSerializer = UsageSnapshot.serializer()
				), element = it
			)
		}.orEmpty().mutable { _, new ->
			store.set(Json.encodeToJsonElement(new))
		}
	}
	
	suspend fun collect(messages: List<SessionMessage>) = cache.update {
		val data = it.toMutableMap()
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
			log.info("Collected usage entries  new={}  total={}", count, data.size)
		}
		
		return@update data.toMap()
	}
	
	fun getSnapshots(): Map<UUID, UsageSnapshot> = cache.get()
}
