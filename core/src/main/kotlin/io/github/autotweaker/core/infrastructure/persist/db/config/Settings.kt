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

@file:Suppress("UNCHECKED_CAST")

package io.github.autotweaker.core.infrastructure.persist.db.config

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.exception.SettingTypeMismatchException
import io.github.autotweaker.api.types.exception.notfound.SettingNotFoundException
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap

class Settings(store: DatabaseStore) : SettingService, Traceable, Loggable,
	DbStore(store, "AppConfig", ConfigTable) {
	private val cache by lazy {
		ConcurrentHashMap(loadAll()).andLog(log) {
			info("Initialized settings  count={}", it.size)
		}
	}
	
	private fun loadAll(): Map<String, SettingValue<*>> = transaction(db) {
		buildMap {
			ConfigTable.selectAll().forEach { row ->
				getValueFromRow(row)?.let { this[row[ConfigTable.keyName]] = it }
			}
		}
	}
	
	override fun <V : SettingValue<T>, T> get(def: SettingDef<V>): T {
		val id = nameOf(def)
		val stored = cache[id]
		
		val result = if (stored != null && stored::class == def.default::class) stored as V else def.default
		return result.value
	}
	
	override fun <V : SettingValue<T>, T> set(def: SettingDef<V>, value: T) {
		val id = nameOf(def)
		val wrapped = when (def.default) {
			is SettingValue.ValByte -> SettingValue(value as Byte)
			is SettingValue.ValShort -> SettingValue(value as Short)
			is SettingValue.ValInt -> SettingValue(value as Int)
			is SettingValue.ValLong -> SettingValue(value as Long)
			is SettingValue.ValFloat -> SettingValue(value as Float)
			is SettingValue.ValDouble -> SettingValue(value as Double)
			is SettingValue.ValBoolean -> SettingValue(value as Boolean)
			is SettingValue.ValChar -> SettingValue(value as Char)
			is SettingValue.ValString -> SettingValue(value as String)
		} as V
		upsertValue(id, wrapped)
		cache[id] = wrapped
		log.debug("Updated setting by def  id={}  value={}", id, wrapped)
	}
	
	fun getAllEntries(): List<SettingEntry> = transaction(db) {
		val stored = ConfigTable.selectAll().associate {
			it[ConfigTable.keyName] to getValueFromRow(it)
		}
		SettingRegistry.getAll().map { (id, def) ->
			SettingEntry(
				id = id,
				value = stored[id] ?: def.default,
			)
		}
	}
	
	fun getDef(id: String): SettingDef<*>? = SettingRegistry.get(id)
	
	fun set(id: String, value: SettingValue<*>) {
		val def = SettingRegistry.get(id) ?: throw SettingNotFoundException(id)
		if (value::class != def.default::class)
			throw SettingTypeMismatchException(id, def.default::class, value::class)
		upsertValue(id, value)
		cache[id] = value
		log.debug("Updated setting by id  id={}  value={}", id, value)
	}
	
	private fun fillColumn(it: UpdateBuilder<*>, value: SettingValue<*>) {
		it[ConfigTable.valJson] = json.encodeToString(SettingValue.serializer(), value)
	}
	
	private fun getValueFromRow(row: ResultRow): SettingValue<*>? =
		trace.catching {
			json.decodeFromString(
				SettingValue.serializer(), row[ConfigTable.valJson]
			)
		}.onFailure { e ->
			log.error("Failed config value deserialization  key={}", row[ConfigTable.keyName], e)
		}.getOrNull()
	
	private fun <V : SettingValue<T>, T> nameOf(def: SettingDef<V>) =
		requireNotNull(def::class.qualifiedName) { "Anonymous SettingDef not supported: ${def::class}" }
	
	private fun upsertValue(id: String, value: SettingValue<*>) {
		trace.catching {
			transaction(db) {
				ConfigTable.upsert {
					it[keyName] = id
					fillColumn(it, value)
				}
			}
		}.onFailure { e ->
			log.error("Failed setting upsert  id={}", id, e)
		}.getOrThrow()
	}
}
