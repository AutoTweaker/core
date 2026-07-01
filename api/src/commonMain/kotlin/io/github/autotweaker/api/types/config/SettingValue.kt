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

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedClassSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed class SettingValue<out T> {
	abstract val value: T
	abstract fun parse(raw: String): SettingValue<T>
	
	@Serializable
	data class ValByte(override val value: Byte) : SettingValue<Byte>() {
		override fun parse(raw: String) = ValByte(raw.toByte())
	}
	
	@Serializable
	data class ValShort(override val value: Short) : SettingValue<Short>() {
		override fun parse(raw: String) = ValShort(raw.toShort())
	}
	
	@Serializable
	data class ValInt(override val value: Int) : SettingValue<Int>() {
		override fun parse(raw: String) = ValInt(raw.toInt())
	}
	
	@Serializable
	data class ValLong(override val value: Long) : SettingValue<Long>() {
		override fun parse(raw: String) = ValLong(raw.toLong())
	}
	
	@Serializable
	data class ValFloat(override val value: Float) : SettingValue<Float>() {
		override fun parse(raw: String) = ValFloat(raw.toFloat())
	}
	
	@Serializable
	data class ValDouble(override val value: Double) : SettingValue<Double>() {
		override fun parse(raw: String) = ValDouble(raw.toDouble())
	}
	
	@Serializable
	data class ValBoolean(override val value: Boolean) : SettingValue<Boolean>() {
		override fun parse(raw: String) = ValBoolean(raw.toBooleanStrict())
	}
	
	@Serializable
	data class ValChar(override val value: Char) : SettingValue<Char>() {
		override fun parse(raw: String) = ValChar(raw.single())
	}
	
	@Serializable
	data class ValString(override val value: String) : SettingValue<String>() {
		override fun parse(raw: String) = ValString(raw)
	}
	
	@OptIn(InternalSerializationApi::class)
	companion object {
		@Suppress("UNCHECKED_CAST")
		fun serializer(): KSerializer<SettingValue<*>> = SealedClassSerializer(
			"io.github.autotweaker.api.types.config.SettingValue",
			SettingValue::class,
			arrayOf(
				ValByte::class, ValShort::class, ValInt::class,
				ValLong::class, ValFloat::class, ValDouble::class,
				ValBoolean::class, ValChar::class, ValString::class,
			) as Array<kotlin.reflect.KClass<out SettingValue<*>>>,
			arrayOf(
				ValByte.serializer(), ValShort.serializer(), ValInt.serializer(),
				ValLong.serializer(), ValFloat.serializer(), ValDouble.serializer(),
				ValBoolean.serializer(), ValChar.serializer(), ValString.serializer(),
			) as Array<KSerializer<out SettingValue<*>>>
		)
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
