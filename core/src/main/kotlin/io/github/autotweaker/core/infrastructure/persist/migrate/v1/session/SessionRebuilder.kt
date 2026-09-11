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
import io.github.autotweaker.api.now
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentContext
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0AgentTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0WorkspaceData
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.*
import kotlinx.serialization.builtins.MapSerializer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.Instant

object SessionRebuilder : MigratorBase() {
	private const val WORKSPACE_NAMESPACE =
		"io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager"
	
	suspend fun prepare(plan: SessionPlanner.SessionPlan) {
		exec(SESSIONS_DB, "ALTER TABLE session_data RENAME TO session_data_v0")
		exec(SESSIONS_DB, "ALTER TABLE agent_data RENAME TO agent_data_v0")
		exec(SESSIONS_DB, "ALTER TABLE session_message RENAME TO session_message_v0")
		transaction(SESSIONS_DB) {
			SchemaUtils.create(
				OwnerTable,
				V1SessionDataTable,
				V1AgentDataTable,
				V1AgentMessageTable,
				V1MessageOwnershipTable,
			)
		}
		fillOwners(plan)
		log.info("Prepared session tables  sessions={}  agents={}", plan.sessions.size, plan.agents.size)
	}
	
	suspend fun finalize(plan: SessionPlanner.SessionPlan, agentTimes: Map<UUID, Pair<Instant, Instant>>) {
		val fallback = now() to now()
		val sessionTimes = plan.sessions.associate { session ->
			session.id to (agentTimes[session.mainId] ?: fallback)
		}
		writeSessionsAndAgents(plan, sessionTimes)
		migrateWorkspaces(sessionTimes, fallback)
		log.info("Rewrote sessions and agents  sessions={}  agents={}", plan.sessions.size, plan.agents.size)
	}
	
	suspend fun finish() {
		exec(SESSIONS_DB, "DROP TABLE _mig_owner")
		exec(SESSIONS_DB, "DROP TABLE session_data_v0")
		exec(SESSIONS_DB, "DROP TABLE agent_data_v0")
		exec(SESSIONS_DB, "DROP TABLE session_message_v0")
	}
	
	private suspend fun fillOwners(plan: SessionPlanner.SessionPlan) {
		val v0Agents = V0AgentTable("agent_data_v0")
		plan.agents.forEach { agent ->
			val contextJson = transaction(SESSIONS_DB) {
				v0Agents.selectAll().where { v0Agents.id eq agent.id }.single()[v0Agents.contextJson]
			}
			val context = json.decodeFromString<V0AgentContext>(contextJson)
			val ids = context.index.ids() + context.droppedMessages.orEmpty()
			ids.chunked(1000).forEach { chunk ->
				transaction(SESSIONS_DB) {
					OwnerTable.batchInsert(chunk) {
						this[OwnerTable.messageId] = it
						this[OwnerTable.agentId] = agent.id
					}
				}
			}
		}
	}
	
	private suspend fun writeSessionsAndAgents(
		plan: SessionPlanner.SessionPlan,
		sessionTimes: Map<UUID, Pair<Instant, Instant>>,
	) {
		transaction(SESSIONS_DB) {
			plan.sessions.forEach { session ->
				val (creation, last) = sessionTimes.getValue(session.id)
				V1SessionDataTable.upsert {
					it[V1SessionDataTable.id] = session.id
					it[V1SessionDataTable.title] = session.title
					it[V1SessionDataTable.overview] = session.overview
					it[V1SessionDataTable.workspaceId] = session.workspaceId
					it[V1SessionDataTable.creationTime] = creation
					it[V1SessionDataTable.lastAccessTime] = last
					it[V1SessionDataTable.agentIndex] = json.parseToJsonElement(session.indexJson)
				}
			}
			plan.agents.forEach { agent ->
				val (creation, last) = sessionTimes.getValue(agent.sessionId)
				V1AgentDataTable.upsert {
					it[V1AgentDataTable.id] = agent.id
					it[V1AgentDataTable.name] = agent.name
					it[V1AgentDataTable.sessionId] = agent.sessionId
					it[V1AgentDataTable.creationTime] = creation
					it[V1AgentDataTable.lastAccessTime] = last
					it[V1AgentDataTable.model] = json.parseToJsonElement(agent.modelJson)
					it[V1AgentDataTable.context] = json.parseToJsonElement(agent.contextJson)
					it[V1AgentDataTable.activeTools] = json.decodeFromString<List<String>>(agent.activeToolsJson)
				}
			}
		}
	}
	
	private suspend fun migrateWorkspaces(
		sessionTimes: Map<UUID, Pair<Instant, Instant>>,
		fallback: Pair<Instant, Instant>,
	) {
		val migrated = migrateJson(
			WORKSPACE_NAMESPACE,
			MapSerializer(UuidSerializer, V0WorkspaceData.serializer()),
			MapSerializer(UuidSerializer, V1WorkspaceData.serializer()),
		) { old ->
			old.mapValues { (_, ws) ->
				val ids = ws.sessionIds.filter { it in sessionTimes }
				val first = ids.firstOrNull()
				val last = ids.lastOrNull()
				V1WorkspaceData(
					id = ws.id,
					path = ws.meta.path,
					displayName = ws.meta.displayName,
					creationTime = if (first == null) fallback.first else sessionTimes.getValue(first).first,
					lastAccessTime = if (last == null) fallback.second else sessionTimes.getValue(last).second,
					sessionIds = ids.toSet(),
				)
			}
		}
		check(migrated) { "Missing workspace data in database '$APP_CONFIG'" }
	}
}
