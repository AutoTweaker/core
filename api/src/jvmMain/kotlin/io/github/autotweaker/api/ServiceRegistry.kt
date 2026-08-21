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

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.api.store.ObjectStorage
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.types.config.SettingValue
import kotlin.reflect.KClass

/**
 * 请不要构造此类或访问此类的伴生对象。
 */
class ServiceRegistry(
	val trace: (KClass<*>) -> TraceRecorder,
	val store: (KClass<*>) -> JsonStore,
	lazyObjects: () -> ObjectStorage,
	lazySetting: () -> SettingService,
	lazyI18n: () -> I18nService,
) {
	val objects: ObjectStorage by lazy { lazyObjects() }
	val setting: SettingService by lazy { lazySetting() }
	val i18n: I18nService by lazy { lazyI18n() }
	
	@PublishedApi
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

/**
 * 对一个 [SettingDef] 调用 [get] 可以直接从数据库拿到当前配置值。
 *
 * 用法：`val prompt: String = SystemPrompt().get()`。
 *
 * @see io.github.autotweaker.api.config.SettingService.get
 */
fun <V : SettingValue<T>, T> SettingDef<V>.get(): T =
	ServiceRegistry.servicesOrError().setting.get(this)

/**
 * 获取一个字符串 [SettingDef] 的当前值，并通过 [java.lang.String.format] 填充字符串占位符。
 */
fun SettingDef<SettingValue.ValString>.format(vararg args: Any?): String =
	ServiceRegistry.servicesOrError().setting.get(this).format(*args)

/**
 * 对一个 [SettingDef] 调用 [set] 可以更新配置值。
 *
 * 用法：`SystemPrompt().set("你是一袋猫粮")`。
 *
 * @see io.github.autotweaker.api.config.SettingService.set
 */
fun <V : SettingValue<T>, T> SettingDef<V>.set(value: T) =
	ServiceRegistry.servicesOrError().setting.set(this, value)
