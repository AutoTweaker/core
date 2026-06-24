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

package io.github.autotweaker.api

import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.trace.TraceRecorder
import kotlin.reflect.KClass

/**
 * 请不要构造此类或访问此类的伴生对象。
 */
class ServiceRegistry(
	val trace: (KClass<*>) -> TraceRecorder,
	val store: (KClass<*>) -> JsonStore,
	val setting: SettingService,
	val i18n: I18nService,
) {
	internal companion object {
		var services: ServiceRegistry? = null
		fun servicesOrError() = services ?: error("Services not initialized")
	}
}

/**
 * 请不要调用此方法。
 */
fun initServices(services: ServiceRegistry) {
	check(ServiceRegistry.services == null) { "Services already initialized" }
	ServiceRegistry.services = services
}
