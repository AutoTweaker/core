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

package io.github.autotweaker.core.adapter.impl.cli.i18n

import java.text.MessageFormat
import java.util.*

object I18n {
	private val bundle: ResourceBundle? get() = I18n_bundle ?: bundledFallback()
	
	@Volatile
	private var I18n_bundle: ResourceBundle? = null
	
	fun init(component: String) {
		I18n_bundle = I18nLoader.fetchBundle(component) { I18n_bundle = it }
	}
	
	private fun bundledFallback(): ResourceBundle? = runCatching {
		ResourceBundle.getBundle("i18n.cli-adapter.messages")
	}.getOrNull()
	
	fun get(key: String, vararg args: Any): String {
		val pattern = bundle?.let { runCatching { it.getString(key) }.getOrNull() } ?: key
		return if (args.isEmpty()) pattern else MessageFormat.format(pattern, *args)
	}
}