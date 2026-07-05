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

import io.github.autotweaker.api.types.config.SettingValue

/**
 * 此接口提供安全的设置读写服务，使用 [SettingDef] 来读取或更新值，而不必手动输入设置项 id。
 *
 * 通过 [io.github.autotweaker.api.Settable] 接口，可以在任何地方获取 [SettingService] 来取值。
 */
interface SettingService {
	/**
	 * 用法：`val prompt: String = setting(SystemPrompt())`。
	 */
	operator fun <V : SettingValue<T>, T> invoke(def: SettingDef<V>): T
	
	/**
	 * 用法：`setting.set(SystemPrompt(), "你是一袋猫粮")`。
	 *
	 * 设置项通常由持有 [io.github.autotweaker.api.adapter.CoreAPI] 的 [io.github.autotweaker.api.adapter.Adapter] 管理，除非特殊需要，不必手动更新设置。
	 */
	fun <V : SettingValue<T>, T> set(def: SettingDef<V>, value: T)
}
