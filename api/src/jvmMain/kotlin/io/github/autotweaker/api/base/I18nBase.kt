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

package io.github.autotweaker.api.base

import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

/**
 * 能够让 [I18nDef] 实现省几行代码的基类，示例：
 *
 * ```kotlin
 * @AutoService(I18nDef::class)
 * class Example : I18nBase(
 *     en("Hello Word"),
 *     zh("你好，世界"),
 *     Locale.FRENCH to "Bonjour le monde",
 *     Locale.of("es", "ES") to "¡Hola mundo!",
 * )
 * ```
 *
 * @see I18nDef
 */
abstract class I18nBase(vararg pairs: Pair<Locale, String>) : I18nDef {
	override val localizations = pairs.toMap()
}

/**
 * 使用 [text] 构造一个语言为 [zh] 的 `Pair<Locale, String>`。
 */
fun zh(text: String): Pair<Locale, String> = zh to text

/**
 * 使用 [text] 构造一个语言为 [en] 的 `Pair<Locale, String>`。
 */
fun en(text: String): Pair<Locale, String> = en to text

/**
 * 语言代码 `zh_CN`。
 */
val zh: Locale = Locale.CHINA

/**
 * 语言代码 `en`。
 */
val en: Locale = Locale.ENGLISH
