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
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceRecorderImpl
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.mockk.mockk

object TestServices {
	private val settingService = object : SettingService {
		@Suppress("UNCHECKED_CAST")
		override operator fun <V : SettingValue<T>, T> invoke(def: SettingDef<V>): T =
			def.default.value
		
		override fun <V : SettingValue<T>, T> set(def: SettingDef<V>, value: T) {}
	}
	
	fun init() {
		try {
			initServices(
				ServiceRegistry(
					TraceRecorderImpl::recorder,
					JsonStoreImpl::namespace,
					{ settingService },
					mockk(relaxed = true)
				)
			)
		} catch (_: IllegalStateException) {
		}
	}
}
