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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config

import kotlinx.serialization.*

@Serializable
sealed class V0SettingValue<out T> {
	abstract val value: T
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValByte")
	data class ValByte(override val value: Byte) : V0SettingValue<Byte>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValShort")
	data class ValShort(override val value: Short) : V0SettingValue<Short>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValInt")
	data class ValInt(override val value: Int) : V0SettingValue<Int>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValLong")
	data class ValLong(override val value: Long) : V0SettingValue<Long>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValFloat")
	data class ValFloat(override val value: Float) : V0SettingValue<Float>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValDouble")
	data class ValDouble(override val value: Double) : V0SettingValue<Double>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValBoolean")
	data class ValBoolean(override val value: Boolean) : V0SettingValue<Boolean>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValChar")
	data class ValChar(override val value: Char) : V0SettingValue<Char>()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.config.SettingValue.ValString")
	data class ValString(override val value: String) : V0SettingValue<String>()
	
	@OptIn(InternalSerializationApi::class)
	companion object {
		@Suppress("UNCHECKED_CAST")
		fun serializer(): KSerializer<V0SettingValue<*>> = SealedClassSerializer(
			"io.github.autotweaker.api.types.config.SettingValue",
			V0SettingValue::class,
			arrayOf(
				ValByte::class, ValShort::class, ValInt::class,
				ValLong::class, ValFloat::class, ValDouble::class,
				ValBoolean::class, ValChar::class, ValString::class,
			) as Array<kotlin.reflect.KClass<out V0SettingValue<*>>>,
			arrayOf(
				ValByte.serializer(), ValShort.serializer(), ValInt.serializer(),
				ValLong.serializer(), ValFloat.serializer(), ValDouble.serializer(),
				ValBoolean.serializer(), ValChar.serializer(), ValString.serializer(),
			) as Array<KSerializer<out V0SettingValue<*>>>
		)
	}
}
