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

import io.github.autotweaker.api.JsonStorable
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.log
import io.github.autotweaker.api.store
import io.github.autotweaker.api.types.i18n.I18nEntry
import io.github.autotweaker.api.types.i18n.LocalizedString
import io.github.autotweaker.api.types.serializer.LocaleSerializer
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

object I18nServiceImpl : I18nService, Loggable, JsonStorable {
	private val cache = atomic(emptyList<I18nEntry>())
	
	@Volatile
	private var language: Locale
	
	init {
		val entry = store.get()
		language = if (entry != null) {
			val data = Json.decodeFromJsonElement<Store>(entry)
			cache.update { cache -> cache + data.entries }
			data.language
		} else Locale.getDefault()
	}
	
	override fun setLanguage(locale: Locale) {
		language = locale
		save()
	}
	
	override fun getLanguage(): Locale = language
	
	override fun get(def: I18nDef): String {
		val key = def::class.qualifiedName ?: error("Anonymous I18nDef not supported: ${def::class}")
		return resolve(key, language)
	}
	
	override fun getDefault(id: String): I18nDef? = I18nRegistry.get(id)
	
	override fun set(id: String, text: String, languageCode: Locale) {
		cache.update { entries ->
			val index = entries.indexOfFirst { it.key == id }
			if (index == -1) error("I18n not found: $id")
			val entry = entries[index]
			entries.toMutableList().also {
				it[index] = entry.copy(localizations = entry.localizations.filter { localizedString ->
					localizedString.languageCode != languageCode
				} + LocalizedString(languageCode, text))
			}
		}
		save()
		log.debug("Set I18n text  key={}  lang={}", id, languageCode.toLanguageTag())
	}
	
	override fun getAll(): List<I18nEntry> {
		val stored = cache.value.associateBy { it.key }
		return I18nRegistry.getAll().map { (key, def) ->
			stored[key] ?: I18nEntry(key, def.localizations)
		}
	}
	
	private fun resolve(key: String, target: Locale): String {
		val localizations = cache.value.find { it.key == key }?.localizations
			?: I18nRegistry.get(key)?.localizations
			?: return key
		localizations.find { it.languageCode == target }?.let { return it.text }
		localizations.find { it.languageCode.language == target.language }?.let { return it.text }
		localizations.find { it.languageCode == Locale.ENGLISH }?.let { return it.text }
		localizations.firstOrNull()?.let { return it.text }
		return key
	}
	
	private fun save() =
		store.set(Json.encodeToJsonElement(Store(cache.value, language)))
	
	
	@Serializable
	private data class Store(
		val entries: List<I18nEntry>,
		@Serializable(with = LocaleSerializer::class)
		val language: Locale
	)
}
