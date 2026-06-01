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

package io.github.autotweaker.core.application

import io.github.autotweaker.api.dev.DbAPI
import io.github.autotweaker.api.dev.DbDebugAPI
import io.github.autotweaker.api.types.dev.*
import io.github.autotweaker.core.infrastructure.data.SecretDbApi
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.persistence.config.ConfigTable
import io.github.autotweaker.core.infrastructure.persistence.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persistence.session.*
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DbDebugAPIImpl : DbDebugAPI {
	private lateinit var appDb: Database
	private lateinit var sessionDb: Database
	
	fun init(databaseStore: DatabaseStore) {
		appDb = databaseStore.connect("AppConfig")
		sessionDb = databaseStore.connect("Sessions")
	}
	
	override val setting: DbAPI<SettingEntry> get() = SettingDbApi
	override val jsonStore: DbAPI<JsonStoreEntry> get() = JsonStoreDbApi
	override val sessionData: DbAPI<SessionDataEntry> get() = SessionDataDbApi
	override val sessionContext: DbAPI<SessionContextEntry> get() = SessionContextDbApi
	override val sessionMessage: DbAPI<SessionMessageEntry> get() = SessionMessageDbApi
	override val secrets: DbAPI<SecretEntry> get() = SecretDbApi
	
	override fun tables(): Map<String, Map<String, Long>> = mapOf(
		"AppConfig" to transaction(appDb) {
			mapOf(
				"core_settings" to ConfigTable.selectAll().count(),
				"json_store" to JsonStoreTable.selectAll().count(),
			)
		},
		"Sessions" to transaction(sessionDb) {
			mapOf(
				"session_data" to SessionDataTable.selectAll().count(),
				"session_context" to SessionContextTable.selectAll().count(),
				"session_message" to SessionMessageTable.selectAll().count(),
			)
		},
		"~/.config/autotweaker/secret" to mapOf(
			"secrets" to SecretManager.list().size.toLong(),
		),
	)
}
