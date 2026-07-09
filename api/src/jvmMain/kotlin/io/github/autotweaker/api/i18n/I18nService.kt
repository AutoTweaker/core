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

package io.github.autotweaker.api.i18n

import java.util.*


/**
 * 此接口提供安全的 i18n 读取服务，使用 [I18nDef] 来读取国际化的文本，而不必手动输入 i18n 条目的 key。
 *
 * 通过 [io.github.autotweaker.api.I18nable] 接口，可以在任何地方获取 [I18nService] 来取值。
 */
interface I18nService {
	/**
	 * 用法：`override val description get() = i18n(Description())`。
	 */
	operator fun invoke(def: I18nDef): String
	
	/**
	 * 获取程序使用的语言，请不要使用 [Locale.getDefault]，通过此 api 获取到的语言可能经过用户配置。
	 */
	fun getLanguage(): Locale
}
