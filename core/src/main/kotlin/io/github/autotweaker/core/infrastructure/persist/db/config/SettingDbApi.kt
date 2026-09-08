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

import io.github.autotweaker.api.types.debug.SettingEntry
import io.github.autotweaker.core.infrastructure.persist.db.base.AbstractDbApi
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.Database

class SettingDbApi(private val store: DatabaseStore) : AbstractDbApi<SettingEntry, String>(
	ConfigTable, ConfigTable.keyName
) {
	override fun connect(): Database = store.connect("AppConfig")
	
	override fun ResultRow.toEntry() = SettingEntry(
		key = this[ConfigTable.keyName],
		byteValue = this[ConfigTable.byteValue],
		shortValue = this[ConfigTable.shortValue],
		intValue = this[ConfigTable.intValue],
		longValue = this[ConfigTable.longValue],
		floatValue = this[ConfigTable.floatValue],
		doubleValue = this[ConfigTable.doubleValue],
		booleanValue = this[ConfigTable.booleanValue],
		charValue = this[ConfigTable.charValue],
		stringValue = this[ConfigTable.stringValue],
	)

	override fun UpsertStatement<Long>.fill(content: SettingEntry) {
		this[ConfigTable.keyName] = content.key
		this[ConfigTable.byteValue] = content.byteValue
		this[ConfigTable.shortValue] = content.shortValue
		this[ConfigTable.intValue] = content.intValue
		this[ConfigTable.longValue] = content.longValue
		this[ConfigTable.floatValue] = content.floatValue
		this[ConfigTable.doubleValue] = content.doubleValue
		this[ConfigTable.booleanValue] = content.booleanValue
		this[ConfigTable.charValue] = content.charValue
		this[ConfigTable.stringValue] = content.stringValue
	}
}
