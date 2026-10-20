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

package io.github.autotweaker.core.application.impl

import io.github.autotweaker.api.debug.DbDebugAPI
import io.github.autotweaker.core.infrastructure.data.SecretDbApi
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import io.github.autotweaker.core.infrastructure.persist.db.config.ConfigTable
import io.github.autotweaker.core.infrastructure.persist.db.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.db.secret.SecretKeyTable
import io.github.autotweaker.core.infrastructure.persist.db.secret.SecretTable
import io.github.autotweaker.core.infrastructure.persist.db.session.*
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageDbApi
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbDebugAPIImpl(
	databaseStore: DatabaseStore,
	override val setting: SettingDbApi,
	override val jsonStore: JsonStoreDbApi,
	override val sessionData: SessionDataDbApi,
	override val agentData: AgentDataDbApi,
	override val agentMessage: AgentMessageDbApi,
	override val usage: UsageDbApi,
	override val secrets: SecretDbApi,
) : DbDebugAPI {
	private val configDb: Database = databaseStore.connect("AppConfig")
	private val sessionDb: Database = databaseStore.connect("Sessions")
	private val usageDb: Database = databaseStore.connect("Usages")
	private val secretDb: Database = databaseStore.connect("Secrets")
	
	private val databases: Map<String, Pair<Database, List<Table>>> = mapOf(
		"AppConfig" to (configDb to listOf(ConfigTable, JsonStoreTable)),
		"Sessions" to (sessionDb to listOf(SessionDataTable, AgentDataTable, AgentMessageTable)),
		"Usages" to (usageDb to listOf(UsageTable)),
		"Secrets" to (secretDb to listOf(SecretTable, SecretKeyTable)),
	)
	
	override suspend fun tables(): Map<String, Map<String, Long>> = databases.mapValues { (_, dbTables) ->
		val (database, tables) = dbTables
		database.transaction {
			tables.associate { it.tableName to it.selectAll().count() }
		}
	}
}
