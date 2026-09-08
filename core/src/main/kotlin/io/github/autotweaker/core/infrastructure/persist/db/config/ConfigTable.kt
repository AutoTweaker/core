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

package io.github.autotweaker.core.infrastructure.persist.db.config

import org.jetbrains.exposed.v1.core.*

object ConfigTable : Table("settings") {
	val keyName = varchar("key_name", 255)
	val byteValue = byte("byte_value").nullable()
	val shortValue = short("short_value").nullable()
	val intValue = integer("int_value").nullable()
	val longValue = long("long_value").nullable()
	val floatValue = float("float_value").nullable()
	val doubleValue = double("double_value").nullable()
	val booleanValue = bool("boolean_value").nullable()
	val charValue = char("char_value", 1).nullable()
	val stringValue = text("string_value").nullable()
	
	override val primaryKey = PrimaryKey(keyName)
	
	init {
		check("single_value") {
			val columns = listOf(
				byteValue, shortValue, intValue, longValue,
				floatValue, doubleValue, booleanValue, charValue, stringValue
			)
			val nonNull = columns.map { it.isNotNull() }
			val atMostOne = nonNull.indices.flatMap { i ->
				(i + 1 until nonNull.size).map { j -> not(nonNull[i] and nonNull[j]) }
			}.reduce { acc, cond -> acc and cond }
			nonNull.reduce { acc, cond -> acc or cond } and atMostOne
		}
	}
}
