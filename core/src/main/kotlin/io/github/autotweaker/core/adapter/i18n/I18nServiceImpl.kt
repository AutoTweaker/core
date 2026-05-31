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
import io.github.autotweaker.api.types.serializer.LocaleSerializer
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.serialization.Serializable
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
	
	@Volatile
	private var initialized = false
	
	@Volatile
	private var language: Locale = Locale.getDefault()
	
	override fun setLanguage(locale: Locale) {
		synchronized(this) {
			this.language = locale
			save()
		}
	}
	
	override fun getLanguage(): Locale {
		ensureInit()
		return language
	}
	
	private fun ensureInit() {
		if (initialized) return
		synchronized(this) {
			if (initialized) return
			load()
			initialized = true
			logger.info("I18nService initialized  stored={}", cache.size)
		}
	}
	
	private fun load() {
		val json = jsonEntry.get() ?: return
		val data = Json.decodeFromJsonElement<Store>(json)
		cache = data.entries
		language = data.language
	}
	
	private fun save() {
		ensureInit()
		jsonEntry.set(Json.encodeToJsonElement(Store(cache, language)))
	}
	
	override fun get(def: I18nDef): String {
		val key = def::class.qualifiedName ?: error("Anonymous I18nDef not supported: ${def::class}")
		return resolve(key, language)
	}
	
	override fun getDefault(id: String): I18nDef? = I18nRegistry.get(id)
	
	override fun set(id: String, text: String, languageCode: Locale) {
		ensureInit()
		synchronized(this) {
			val mutable = cache.toMutableList()
			val index = mutable.indexOfFirst { it.key == id }
			if (index >= 0) {
				val entry = mutable[index]
				mutable[index] = entry.copy(localizations = entry.localizations.filter {
					it.languageCode != languageCode
				} + LocalizedString(languageCode, text))
			} else {
				val base = I18nRegistry.get(id)?.localizations ?: emptyList()
				mutable.add(
					I18nEntry(
						id,
						base.filter { it.languageCode != languageCode } + LocalizedString(languageCode, text)))
			}
			cache = mutable
			save()
		}
		logger.debug("I18n text set  key={}  lang={}", id, languageCode.toLanguageTag())
	}
	
	override fun getAll(): List<I18nEntry> {
		ensureInit()
		val stored = cache.associateBy { it.key }
		return I18nRegistry.getAll().map { (key, def) ->
			stored[key] ?: I18nEntry(key, def.localizations)
		}
	}
	
	private fun resolve(key: String, target: Locale): String {
		ensureInit()
		val localizations =
			cache.find { it.key == key }?.localizations ?: I18nRegistry.get(key)?.localizations ?: return key
		localizations.find { it.languageCode == target }?.let { return it.text }
		if (target.language.isNotEmpty()) {
			localizations.find { it.languageCode.language == target.language }?.let { return it.text }
		}
		localizations.find { it.languageCode == Locale.ENGLISH }?.let { return it.text }
		localizations.firstOrNull()?.let { return it.text }
		return key
	}
	
	@Serializable
	private data class Store(
		val entries: List<I18nEntry>, val language: @Serializable(with = LocaleSerializer::class) Locale
	)
}
