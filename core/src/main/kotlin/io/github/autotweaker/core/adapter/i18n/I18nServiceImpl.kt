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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.i18n.I18nEntry
import io.github.autotweaker.api.types.i18n.LocalizedString
import io.github.autotweaker.api.types.serializer.LocaleSerializer
import io.github.autotweaker.core.infrastructure.persist.json.base.AtomicStore
import kotlinx.serialization.Serializable
import java.util.*

object I18nServiceImpl : AtomicStore<I18nServiceImpl.Data>(), I18nService, Loggable {
	override val serializer = Data.serializer()
	override fun default() = Data()
	
	override fun setLanguage(locale: Locale) {
		update { it.copy(language = locale) }
		log.debug("Updated language  lang={}", locale.toLanguageTag())
	}
	
	override fun getLanguage(): Locale = get().language
	
	override fun get(def: I18nDef): String {
		val key = def::class.qualifiedName ?: error("Anonymous I18nDef not supported: ${def::class}")
		return resolve(key, get().language)
	}
	
	override fun getDefault(id: String): I18nDef? = I18nRegistry.get(id)
	
	override fun set(id: String, text: String, languageCode: Locale) {
		update { data ->
			val index = data.entries.indexOfFirst { it.key == id }
			if (index == -1) error("I18n not found: $id")
			val entry = data.entries[index]
			data.copy(entries = data.entries.toMutableList().also {
				it[index] = entry.copy(localizations = entry.localizations.filter { localizedString ->
					localizedString.languageCode != languageCode
				} + LocalizedString(languageCode, text))
			})
		}
		log.debug("Set I18n text  key={}  lang={}", id, languageCode.toLanguageTag())
	}
	
	override fun getAll(): List<I18nEntry> {
		val stored = get().entries.associateBy { it.key }
		return I18nRegistry.getAll().map { (key, def) ->
			stored[key] ?: I18nEntry(key, def.localizations)
		}
	}
	
	private fun resolve(key: String, target: Locale): String {
		val localizations = get().entries.find { it.key == key }?.localizations
			?: I18nRegistry.get(key)?.localizations
			?: return key
		localizations.find { it.languageCode == target }?.let { return it.text }
		localizations.find { it.languageCode.language == target.language }?.let { return it.text }
		localizations.find { it.languageCode == en }?.let { return it.text }
		localizations.firstOrNull()?.let { return it.text }
		return key
	}
	
	@Serializable
	data class Data(
		val entries: List<I18nEntry> = emptyList(),
		@Serializable(with = LocaleSerializer::class)
		val language: Locale = Locale.getDefault(),
	)
}
