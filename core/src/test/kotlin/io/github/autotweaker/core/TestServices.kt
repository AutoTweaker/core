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

package io.github.autotweaker.core

import io.github.autotweaker.api.ServiceRegistry
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.initServices
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceRecorderImpl
import io.mockk.mockk
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.*

object TestServices {
	private val settingService = object : SettingService {
		@Suppress("UNCHECKED_CAST")
		override fun <V : SettingValue<T>, T> get(def: SettingDef<V>): T =
			def.default.value
		
		override fun <V : SettingValue<T>, T> set(def: SettingDef<V>, value: T) {}
	}
	
	val jsonStore = mockk<JsonStoreImpl>(relaxed = true)
	
	val secretMap = mutableMapOf<UUID, String>()
	val removedSecrets = mutableListOf<UUID>()
	val secretStore = object : SecretStore {
		override suspend fun set(secret: String, id: UUID) {
			secretMap[id] = secret
		}
		
		override suspend fun get(id: UUID): String = secretMap[id]!!
		override suspend fun list(): List<UUID> = secretMap.keys.toList()
		override suspend fun remove(id: UUID): Boolean = removedSecrets.add(id).let { secretMap.remove(id) != null }
		override fun requireUnlocked() {}
	}
	
	fun init() {
		try {
			if (GlobalContext.getOrNull() == null) {
				startKoin { modules(module { single<SecretStore> { secretStore } }) }
			}
			initServices(
				ServiceRegistry(
					TraceRecorderImpl(mockk(), mockk())::recorder,
					jsonStore::namespace,
					{ mockk(relaxed = true) },
					{ settingService },
					{ mockk(relaxed = true) }
				)
			)
		} catch (_: IllegalStateException) {
		}
	}
}
