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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.exception.SettingTypeMismatchException
import io.github.autotweaker.api.types.exception.notfound.SettingNotFoundException
import io.github.autotweaker.core.infrastructure.persist.db.transaction
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap

object Settings : SettingService, Loggable, Traceable {
	private lateinit var db: Database
	
	private val cache = ConcurrentHashMap<String, SettingValue<*>>()
	
	private fun fillColumn(it: UpdateBuilder<*>, value: SettingValue<*>) {
		it[ConfigTable.valJson] = json.encodeToString(SettingValue.serializer(), value)
	}
	
	private fun getValueFromRow(row: ResultRow): SettingValue<*>? {
		val jsonStr = row[ConfigTable.valJson]
		return trace.catching { json.decodeFromString(SettingValue.serializer(), jsonStr) }
			.onFailure { log.warn("Failed config value deserialization  key={}", row[ConfigTable.keyName]) }
			.getOrNull()
	}
	
	suspend fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("AppConfig")
		db.transaction {
			SchemaUtils.create(ConfigTable)
		}
		cache.putAll(loadAll())
		log.info("Initialized settings  count={}", cache.size)
	}
	
	private suspend fun loadAll(): Map<String, SettingValue<*>> = db.transaction {
		val map = mutableMapOf<String, SettingValue<*>>()
		ConfigTable.selectAll().forEach { row ->
			getValueFromRow(row)?.let { map[row[ConfigTable.keyName]] = it }
		}
		return@transaction map
	}
	
	override fun <V : SettingValue<T>, T> get(def: SettingDef<V>): T {
		val id = requireDef(def)
		val stored = cache[id]
		
		@Suppress("UNCHECKED_CAST")
		val result = if (stored != null && stored::class == def.default::class) stored as V else def.default
		return result.value
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <V : SettingValue<T>, T> set(def: SettingDef<V>, value: T) {
		val id = requireDef(def)
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
	
	private fun <V : SettingValue<T>, T> requireDef(def: SettingDef<V>) =
		requireNotNull(def::class.qualifiedName) { "Anonymous SettingDef not supported: ${def::class}" }
	
	fun setById(id: String, value: SettingValue<*>) {
		val def = SettingRegistry.get(id) ?: throw SettingNotFoundException(id)
		if (value::class != def.default::class)
			throw SettingTypeMismatchException(id, def.default::class, value::class)
		upsertValue(id, value)
		cache[id] = value
		log.debug("Updated setting by id  id={}  value={}", id, value)
	}
	
	private fun upsertValue(id: String, value: SettingValue<*>) {
		trace.catching {
			transaction(db) {
				ConfigTable.upsert {
					it[keyName] = id
					fillColumn(it, value)
				}
			}
		}.rethrowCancellation()
			.onFailure { e ->
				log.error("Failed setting upsert  id={}", id, e)
			}.getOrThrow()
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
}
