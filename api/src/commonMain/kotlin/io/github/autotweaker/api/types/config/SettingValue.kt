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

package io.github.autotweaker.api.types.config

import kotlinx.serialization.Serializable

@Serializable
sealed class SettingValue {
	abstract val value: Any?
	abstract fun parse(raw: String): SettingValue
	
	@Serializable
	data class ValByte(override val value: Byte) : SettingValue() {
		override fun parse(raw: String) = ValByte(raw.toByte())
	}
	
	@Serializable
	data class ValShort(override val value: Short) : SettingValue() {
		override fun parse(raw: String) = ValShort(raw.toShort())
	}
	
	@Serializable
	data class ValInt(override val value: Int) : SettingValue() {
		override fun parse(raw: String) = ValInt(raw.toInt())
	}
	
	@Serializable
	data class ValLong(override val value: Long) : SettingValue() {
		override fun parse(raw: String) = ValLong(raw.toLong())
	}
	
	@Serializable
	data class ValFloat(override val value: Float) : SettingValue() {
		override fun parse(raw: String) = ValFloat(raw.toFloat())
	}
	
	@Serializable
	data class ValDouble(override val value: Double) : SettingValue() {
		override fun parse(raw: String) = ValDouble(raw.toDouble())
	}
	
	@Serializable
	data class ValBoolean(override val value: Boolean) : SettingValue() {
		override fun parse(raw: String) = ValBoolean(raw.toBooleanStrict())
	}
	
	@Serializable
	data class ValChar(override val value: Char) : SettingValue() {
		override fun parse(raw: String) = ValChar(raw.single())
	}
	
	@Serializable
	data class ValString(override val value: String) : SettingValue() {
		override fun parse(raw: String) = ValString(raw)
	}
}

fun SettingValue(value: Byte) = SettingValue.ValByte(value)
fun SettingValue(value: Short) = SettingValue.ValShort(value)
fun SettingValue(value: Int) = SettingValue.ValInt(value)
fun SettingValue(value: Long) = SettingValue.ValLong(value)
fun SettingValue(value: Float) = SettingValue.ValFloat(value)
fun SettingValue(value: Double) = SettingValue.ValDouble(value)
fun SettingValue(value: Boolean) = SettingValue.ValBoolean(value)
fun SettingValue(value: Char) = SettingValue.ValChar(value)
fun SettingValue(value: String) = SettingValue.ValString(value)
