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
import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.agent.AgentMessageType
import io.github.autotweaker.core.infrastructure.persist.db.session.MessageSearch
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0ContentPart
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.agent.V1AgentMessage
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.agent.typeOfV1
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1AgentMessageTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1MessageOwnershipTable
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.*

object SessionIndexRebuilder : MigratorBase() {
	suspend fun rebuild() {
		var lastId: UUID? = null
		var count = 0L
		while (true) {
			val batch = transaction(SESSIONS_DB) {
				val from = lastId
				val query = if (from == null) V1AgentMessageTable.selectAll()
				else V1AgentMessageTable.selectAll().where { V1AgentMessageTable.id greater from }
				query.orderBy(V1AgentMessageTable.id).limit(500)
					.map { it[V1AgentMessageTable.id] to it[V1AgentMessageTable.content] }
			}
			if (batch.isEmpty()) break
			lastId = batch.last().first
			count += batch.size
			val ownership = batch.map { (id, content) ->
				val message = json.decodeFromJsonElement<V1AgentMessage>(content)
				MessageSearch.upsert(
					id,
					AgentMessageType.valueOf(typeOfV1(message).name),
					message.timestamp,
					searchTextOf(message),
				)
				id to message.origin.single()
			}
			transaction(SESSIONS_DB) {
				V1MessageOwnershipTable.batchInsert(ownership) {
					this[V1MessageOwnershipTable.messageId] = it.first
					this[V1MessageOwnershipTable.agentId] = it.second
				}
			}
		}
		log.info("Rebuilt message index  count={}", count)
	}
	
	private fun searchTextOf(message: V1AgentMessage): String? = when (message) {
		is V1AgentMessage.User -> message.content.content?.filterIsInstance<V0ContentPart.Text>()
			?.joinToString("\n") { it.content }?.orNull()
		
		is V1AgentMessage.Assistant -> message.content?.orNull()
		is V1AgentMessage.Tool.Call -> message.arguments
		is V1AgentMessage.Tool.Result -> message.content
		is V1AgentMessage.Compact -> message.content
		is V1AgentMessage.UsageRecord -> null
	}
}
