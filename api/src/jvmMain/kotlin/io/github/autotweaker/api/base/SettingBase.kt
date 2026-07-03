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

abstract class ByteSetting(
	default: Byte, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValByte>(*desc) {
	override val default = SettingValue(default)
}

abstract class ShortSetting(
	default: Short, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValShort>(*desc) {
	override val default = SettingValue(default)
}

abstract class IntSetting(
	default: Int, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValInt>(*desc) {
	override val default = SettingValue(default)
}

abstract class LongSetting(
	default: Long, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValLong>(*desc) {
	override val default = SettingValue(default)
}

abstract class FloatSetting(
	default: Float, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValFloat>(*desc) {
	override val default = SettingValue(default)
}

abstract class DoubleSetting(
	default: Double, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValDouble>(*desc) {
	override val default = SettingValue(default)
}

abstract class BooleanSetting(
	default: Boolean, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValBoolean>(*desc) {
	override val default = SettingValue(default)
}

abstract class CharSetting(
	default: Char, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValChar>(*desc) {
	override val default = SettingValue(default)
}

abstract class StringSetting(
	default: String, vararg desc: Pair<Locale, String>
) : SettingBase<SettingValue.ValString>(*desc) {
	override val default = SettingValue(default)
}


abstract class SettingBase<out V : SettingValue<*>>(
	vararg desc: Pair<Locale, String>
) : SettingDef<V> {
	override val description = desc.toMap()
}
