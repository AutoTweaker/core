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

package io.github.autotweaker.core.infrastructure.i18n

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.store.AtomicStore
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.log
import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.I18nEntries
import io.github.autotweaker.api.types.Localizations
import io.github.autotweaker.api.types.serializer.LocaleSerializer
import kotlinx.serialization.Serializable
import java.util.*

object I18nServiceImpl : AtomicStore<I18nServiceImpl.Data>(), I18nService, Loggable {
	override val serializer = Data.serializer()
	override fun default() = Data()
	
	fun setLanguage(locale: Locale) =
		update {
			it.copy(language = locale)
		}.andLog(log) {
			debug("Updated language  lang={}", locale.toLanguageTag())
		}
	
	
	fun getLanguage(): Locale = get().language
	
	override fun invoke(def: I18nDef): String {
		val key = requireNotNull(def::class.qualifiedName)
		{ "Anonymous I18nDef not supported: ${def::class}" }
		return resolve(key, get().language)
	}
	
	fun getDefault(id: String): Localizations? = I18nRegistry.get(id)
	
	fun set(id: String, text: String, languageCode: Locale) {
		if (I18nRegistry.get(id) == null) error("I18n not found: $id")
		update { data ->
			val entries = data.entries.toMutableMap()
			val localizations = entries[id].orEmpty().toMutableMap()
			localizations[languageCode] = text
			entries[id] = localizations
			data.copy(entries = entries)
		}
		log.debug("Set I18n text  key={}  lang={}", id, languageCode.toLanguageTag())
	}
	
	fun getAllEntries(): I18nEntries = mergeAll()
	
	fun resolveByKey(id: String): String = resolve(id, getLanguage())
	
	private fun resolve(key: String, target: Locale): String {
		val loc = getLocalizations(key) ?: return key
		loc[target]?.let { return it }
		loc.entries.find { it.key.language == target.language }?.let { return it.value }
		loc.entries.find { it.key.language == en.language }?.let { return it.value }
		loc.values.firstOrNull()?.let { return it }
		return key
	}
	
	private fun getLocalizations(key: String): Localizations? =
		(I18nRegistry.get(key).orEmpty() + get().entries[key].orEmpty()).orNull()
	
	private fun mergeAll(): I18nEntries {
		val cache = get().entries
		return I18nRegistry.getAll().mapValues { (key, localizations) ->
			val cached = cache[key]
			if (cached == null) localizations
			else localizations + cached
		}
	}
	
	
	@Serializable
	data class Data(
		val entries: Map<String, Map<@Serializable(with = LocaleSerializer::class) Locale, String>> = mapOf(),
		@Serializable(with = LocaleSerializer::class)
		val language: Locale = Locale.getDefault(),
	)
}
