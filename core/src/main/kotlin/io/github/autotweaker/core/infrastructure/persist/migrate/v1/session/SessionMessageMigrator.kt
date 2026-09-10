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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1.session

import io.github.autotweaker.api.json
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentMessage
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0MessageTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.agent.toV1
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.agent.typeOfV1
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1AgentMessageTable
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.Instant

object SessionMessageMigrator : MigratorBase() {
	suspend fun migrate(): Map<UUID, Pair<Instant, Instant>> {
		val v0Messages = V0MessageTable("session_message_v0")
		val agentTimes = mutableMapOf<UUID, Pair<Instant, Instant>>()
		var orphanCount = 0L
		var lastId: UUID? = null
		while (true) {
			val batch = transaction(SESSIONS_DB) {
				val from = lastId
				val query = if (from == null) v0Messages.selectAll()
				else v0Messages.selectAll().where { v0Messages.id greater from }
				query.orderBy(v0Messages.id).limit(500)
					.map { it[v0Messages.id] to it[v0Messages.contentJson] }
			}
			if (batch.isEmpty()) break
			lastId = batch.last().first
			val owners = transaction(SESSIONS_DB) {
				OwnerTable.selectAll().where { OwnerTable.messageId inList batch.map { it.first } }
					.associate { it[OwnerTable.messageId] to it[OwnerTable.agentId] }
			}
			transaction(SESSIONS_DB) {
				batch.forEach { (id, raw) ->
					val owner = owners[id]
					if (owner == null) {
						orphanCount++
						return@forEach
					}
					val message = json.decodeFromString<V0AgentMessage>(raw)
					val migrated = message.toV1(setOf(owner))
					V1AgentMessageTable.upsert {
						it[V1AgentMessageTable.id] = id
						it[V1AgentMessageTable.type] = typeOfV1(migrated)
						it[V1AgentMessageTable.timestamp] = migrated.timestamp
						it[V1AgentMessageTable.content] = json.encodeToJsonElement(migrated)
					}
					val current = agentTimes[owner]
					agentTimes[owner] = when {
						current == null -> migrated.timestamp to migrated.timestamp
						migrated.timestamp < current.first -> migrated.timestamp to current.second
						migrated.timestamp > current.second -> current.first to migrated.timestamp
						else -> current
					}
				}
			}
		}
		log.info("Migrated messages  orphanDropped={}", orphanCount)
		return agentTimes
	}
}
