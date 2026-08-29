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

import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.debug.DbAPI
import io.github.autotweaker.api.debug.DbDebugAPI
import io.github.autotweaker.api.types.debug.*
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.data.SecretDbApi
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import io.github.autotweaker.core.infrastructure.persist.db.config.ConfigTable
import io.github.autotweaker.core.infrastructure.persist.db.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.db.session.*
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageDbApi
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.Koin
import java.util.*

class DbDebugAPIImpl(private val koin: Koin) : DbDebugAPI {
	private val databaseStore: DatabaseStore = koin.get()
	private val secretStore: SecretStore = koin.get()
	private val configDb: Database = databaseStore.connect("AppConfig")
	private val sessionDb: Database = databaseStore.connect("Sessions")
	private val usageDb: Database = databaseStore.connect("Usages")
	
	override val setting: DbAPI<SettingEntry, String> get() = koin.get<SettingDbApi>()
	override val jsonStore: DbAPI<JsonStoreEntry, String> get() = koin.get<JsonStoreDbApi>()
	override val sessionData: DbAPI<SessionDataEntry, UUID> get() = koin.get<SessionDataDbApi>()
	override val agentData: DbAPI<AgentDataEntry, UUID> get() = koin.get<AgentDataDbApi>()
	override val sessionMessage: DbAPI<SessionMessageEntry, UUID> get() = koin.get<SessionMessageDbApi>()
	override val usage: DbAPI<UsageEntry, UUID> get() = koin.get<UsageDbApi>()
	override val secrets: DbAPI<SecretEntry, UUID> get() = koin.get<SecretDbApi>()
	
	override suspend fun tables(): Map<String, Map<String, Long>> = mapOf(
		"AppConfig" to configDb.transaction {
			mapOf(
				"settings" to ConfigTable.selectAll().count(),
				"json_store" to JsonStoreTable.selectAll().count(),
			)
		},
		"Sessions" to sessionDb.transaction {
			mapOf(
				"session_data" to SessionDataTable.selectAll().count(),
				"agent_data" to AgentDataTable.selectAll().count(),
				"session_message" to SessionMessageTable.selectAll().count(),
			)
		},
		"Usages" to usageDb.transaction {
			mapOf(
				"usage_record" to UsageTable.selectAll().count(),
			)
		},
		"~/.config/$APP_NAME_LOWERCASE/secret" to mapOf(
			"secrets" to secretStore.list().size.toLong(),
		),
	)
}
