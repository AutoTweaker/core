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

package io.github.autotweaker.api.i18n

import io.github.autotweaker.api.types.i18n.I18nEntry
import java.util.*

interface I18nService {
	fun get(def: I18nDef): String
	fun getDefault(id: String): I18nDef?
	fun set(id: String, text: String, languageCode: Locale)
	fun getAll(): List<I18nEntry>
	fun setLanguage(locale: Locale)
	fun getLanguage(): Locale
}
