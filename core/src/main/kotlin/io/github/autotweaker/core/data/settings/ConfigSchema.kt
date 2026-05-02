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

package io.github.autotweaker.core.data.settings

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table

object ConfigTable : Table("core_settings") {
	val keyName = varchar("key_name", 255)
	val valJson = text("val_json")
	val description = text("description")
	
	override val primaryKey = PrimaryKey(keyName)
}

@Serializable
data class SettingItem(
	val key: SettingKey,
	val value: Value,
	val description: String
) {
	@Serializable
	sealed class Value {
		abstract val value: Any?
		
		//基本类型
		@Serializable
		data class ValByte(
			override val value: Byte
		) : Value()
		
		@Serializable
		data class ValShort(
			override val value: Short
		) : Value()
		
		@Serializable
		data class ValInt(
			override val value: Int
		) : Value()
		
		@Serializable
		data class ValLong(
			override val value: Long
		) : Value()
		
		@Serializable
		data class ValFloat(
			override val value: Float
		) : Value()
		
		@Serializable
		data class ValDouble(
			override val value: Double
		) : Value()
		
		@Serializable
		data class ValBoolean(
			override val value: Boolean
		) : Value()
		
		@Serializable
		data class ValChar(
			override val value: Char
		) : Value()
		
		@Serializable
		data class ValString(
			override val value: String
		) : Value()
	}
}

inline fun <reified T> List<SettingItem>.find(key: String): T {
	val value = find { it.key == SettingKey(key) }?.value
		?: throw IllegalArgumentException("Setting not found: $key")
	return value.value as? T
		?: throw IllegalArgumentException("Setting type mismatch for $key: expected ${T::class.simpleName}, got ${value::class.simpleName}")
}
