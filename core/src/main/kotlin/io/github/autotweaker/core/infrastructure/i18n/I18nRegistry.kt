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

import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.I18nEntries
import io.github.autotweaker.api.types.Localizations
import io.github.autotweaker.core.infrastructure.loadClass
import io.github.autotweaker.core.infrastructure.persist.db.config.SettingRegistry

object I18nRegistry {
	private val defs: I18nEntries by lazy {
		val setting = SettingRegistry.getAll().mapValues { it.value.description }
		val i18n = loadClass<I18nDef>().mapNotNull {
			(it::class.qualifiedName ?: return@mapNotNull null) to it.localizations
		}.toMap()
		return@lazy setting + i18n
	}
	
	fun get(key: String): Localizations? = defs[key]
	fun getAll(): I18nEntries = defs
}
