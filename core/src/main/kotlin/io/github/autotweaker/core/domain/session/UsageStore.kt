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
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import java.util.*

object UsageStore : Loggable, MutableStore<MutableMap<UUID, Usage>>() {
	override val serializer = MutableMapSerializer(
		UuidSerializer,
		Usage.serializer()
	)
	
	override fun default() = mutableMapOf<UUID, Usage>()
	
	suspend fun collect(messages: List<AgentMessage>) = transform { cache ->
		messages.forEach { message ->
			when (message) {
				is AgentMessage.Assistant -> message.usage?.let {
					cache[message.id] = it
				}
				
				is AgentMessage.Compact -> message.usage?.let {
					cache[message.id] = it
				}
				
				is AgentMessage.UsageRecord -> cache[message.id] = message.usage
				else -> {}
			}
		}.andLog(log) {
			info("Collected usage entries  total={}", cache.size)
		}
	}
	
	suspend fun getAll() = transform { it.toMap() }
}
