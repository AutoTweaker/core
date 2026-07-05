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

package io.github.autotweaker.api.config

import io.github.autotweaker.api.types.Localizations
import io.github.autotweaker.api.types.config.SettingValue

/**
 * 实现此接口并打上 `@AutoService(SettingDef::class)` 注释即可注册一个设置。
 *
 * 使用 [io.github.autotweaker.api.base.IntSetting] 等基类可以省去一些声明设置的重复代码。
 *
 * 实现 [io.github.autotweaker.api.Settable] 接口即可通过 [SettingService] 获取设置的当前值。
 *
 * 通过 [io.github.autotweaker.api.adapter.CoreAPI.ConfigAPI] 可管理所有设置。
 *
 * AutoTweaker 通过 SPI 和 [SettingService] 来实现分布式的设置声明以及安全的取值。
 */
interface SettingDef<out V : SettingValue<*>> {
	/**
	 * 设置的默认值。
	 */
	val default: V
	
	/**
	 * 设置的多语言描述，由 i18n 服务管理，也同样支持自动翻译。
	 *
	 * @see io.github.autotweaker.api.i18n.I18nDef
	 */
	val description: Localizations
}
