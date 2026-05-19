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

import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

private val json = Json { ignoreUnknownKeys = true }

fun fillColumn(it: UpdateBuilder<*>, value: SettingValue) {
	it[ConfigTable.valJson] = json.encodeToString(SettingValue.serializer(), value)
}

fun getValueFromRow(row: ResultRow): SettingValue? {
	val jsonStr = row[ConfigTable.valJson]
	return try {
		json.decodeFromString(SettingValue.serializer(), jsonStr)
	} catch (_: Exception) {
		null
	}
}
