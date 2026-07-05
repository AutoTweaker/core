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

@file:Suppress("unused")

package io.github.autotweaker.api.base

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue
import java.util.*

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class ByteSetting(
	default: Byte, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValByte>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class ShortSetting(
	default: Short, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValShort>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class IntSetting(
	default: Int, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValInt>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class LongSetting(
	default: Long, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValLong>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class FloatSetting(
	default: Float, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValFloat>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class DoubleSetting(
	default: Double, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValDouble>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class BooleanSetting(
	default: Boolean, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValBoolean>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * 没有示例，看 [StringSetting] 的文档，用法都一样只是 [default] 类型不一样。
 *
 * @see StringSetting
 * @see SettingDef
 */
abstract class CharSetting(
	default: Char, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValChar>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 能够让 [SettingDef] 实现省几行代码的基类，示例：
 *
 * ```kotlin
 * @AutoService(SettingDef::class)
 * class SystemPrompt : StringSetting(
 * 	"你是一袋猫粮", zh("系统提示词")
 * )
 * ```
 *
 * @see SettingDef
 */
abstract class StringSetting(
	default: String, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValString>(*desc) {
	override val default = SettingValue(default)
}

/**
 * 通常使用此文件中的其他基类，如 [StringSetting]。
 */
abstract class SettingBase<out V : SettingValue<*>>(
	vararg desc: Pair<Locale, String>
) : SettingDef<V> {
	override val description = desc.toMap()
}
