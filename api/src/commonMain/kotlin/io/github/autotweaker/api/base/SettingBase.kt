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

abstract class ByteSetting(
	default: Byte, override val description: String
) : SettingDef<SettingValue.ValByte> {
	override val default = SettingValue(default)
}

abstract class ShortSetting(
	default: Short, override val description: String
) : SettingDef<SettingValue.ValShort> {
	override val default = SettingValue(default)
}

abstract class IntSetting(
	default: Int, override val description: String
) : SettingDef<SettingValue.ValInt> {
	override val default = SettingValue(default)
}

abstract class LongSetting(
	default: Long, override val description: String
) : SettingDef<SettingValue.ValLong> {
	override val default = SettingValue(default)
}

abstract class FloatSetting(
	default: Float, override val description: String
) : SettingDef<SettingValue.ValFloat> {
	override val default = SettingValue(default)
}

abstract class DoubleSetting(
	default: Double, override val description: String
) : SettingDef<SettingValue.ValDouble> {
	override val default = SettingValue(default)
}

abstract class BooleanSetting(
	default: Boolean, override val description: String
) :
	SettingDef<SettingValue.ValBoolean> {
	override val default = SettingValue(default)
}

abstract class CharSetting(
	default: Char, override val description: String
) : SettingDef<SettingValue.ValChar> {
	override val default = SettingValue(default)
}

abstract class StringSetting(
	default: String, override val description: String
) : SettingDef<SettingValue.ValString> {
	override val default = SettingValue(default)
}
