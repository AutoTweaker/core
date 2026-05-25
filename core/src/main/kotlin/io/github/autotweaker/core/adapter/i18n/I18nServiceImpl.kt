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

package io.github.autotweaker.core.adapter.i18n

import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.i18n.I18nEntry
import io.github.autotweaker.api.types.i18n.LocalizedString
import io.github.autotweaker.core.data.json.JsonStoreImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.*

object I18nServiceImpl : I18nService {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(this::class)
	
	@Volatile
	private var cache: List<I18nEntry> = emptyList()
	private var initialized = false
	
	private fun ensureInit() {
		if (initialized) return
		synchronized(this) {
			if (initialized) return
			load()
			if (cache.isEmpty()) {
				cache = seedFromRegistry()
				save()
			}
			initialized = true
			logger.info("I18nService initialized  entries={}", cache.size)
		}
	}
	
	private fun load() {
		val json = jsonEntry.get() ?: return
		cache = Json.decodeFromJsonElement(json)
	}
	
	private fun save() {
		jsonEntry.set(Json.encodeToJsonElement(cache))
	}
	
	private fun seedFromRegistry(): List<I18nEntry> =
		I18nRegistry.getAll().map { (key, def) -> I18nEntry(key, def.localizations) }
	
	override fun get(def: I18nDef): String {
		val key = def::class.qualifiedName!!
		return resolve(key, Locale.getDefault())
	}
	
	override fun getDefault(id: String): I18nDef? = I18nRegistry.get(id)
	
	override fun set(id: String, text: String, languageCode: Locale) {
		ensureInit()
		synchronized(this) {
			val index = cache.indexOfFirst { it.key == id }
			require(index >= 0) { "Unknown i18n key: $id" }
			val entry = cache[index]
			cache = cache.toMutableList().apply {
				this[index] = entry.copy(localizations = entry.localizations.filter {
					it.languageCode != languageCode
				} + LocalizedString(languageCode, text))
			}
			save()
		}
		logger.debug("I18n text set  key={}  lang={}", id, languageCode.toLanguageTag())
	}
	
	override fun getAll(): List<I18nEntry> {
		ensureInit()
		return cache
	}
	
	private fun resolve(key: String, target: Locale): String {
		ensureInit()
		val localizations = cache.find { it.key == key }?.localizations ?: return key
		localizations.find { it.languageCode == target }?.let { return it.text }
		if (target.language.isNotEmpty()) {
			localizations.find { it.languageCode.language == target.language }?.let { return it.text }
		}
		localizations.find { it.languageCode == Locale.ENGLISH }?.let { return it.text }
		localizations.firstOrNull()?.let { return it.text }
		return key
	}
}
