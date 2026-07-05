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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.SessionMessage
import java.util.*

object UsageStore : MutableStore<MutableMap<UUID, UsageSnapshot>>(), Loggable {
	override val serializer = MutableMapSerializer(
		UuidSerializer, UsageSnapshot.serializer()
	)
	
	override fun default() = mutableMapOf<UUID, UsageSnapshot>()
	
	suspend fun collect(messages: List<SessionMessage>) = transform {
		var count = 0
		
		fun add(key: UUID, snapshot: UsageSnapshot) {
			if (key !in it) {
				it[key] = snapshot
				count++
			}
		}
		
		messages.forEach { message ->
			when (message) {
				is SessionMessage.Assistant -> message.usageSnapshot?.let { add(message.id, it) }
				is SessionMessage.Compact -> message.snapshots?.forEach { (id, snapshot) -> add(id, snapshot) }
				is SessionMessage.UsageRecord -> add(message.id, message.snapshot)
				else -> {}
			}
		}
		
		if (count > 0) log.info("Collected usage entries  new={}  total={}", count, it.size)
	}
	
	suspend fun getSnapshots(): Map<UUID, UsageSnapshot> = transform { it.toMap() }
}
