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

package io.github.autotweaker.adapter.cli.commands.secret.key

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI

class KeyManager(
	private val core: CoreAPI
) : I18nable {
	suspend fun Console.list() {
		core.config.listApiKey().values.sorted().forEach { out(it) }
	}
	
	suspend fun Console.add(name: String) {
		if (name.isBlank()) error(KeyI18n.EmptyNameError())
		
		val key = promptOrStdin(KeyI18n.PromptInputApiKey(), echo = false)
		
		if (key.isBlank()) error(KeyI18n.EmptyKeyError())
		
		core.config.addApiKey(name, key)
	}
	
	suspend fun Console.remove(name: String) {
		val success = core.config.removeApiKey(name)
		if (!success) error(KeyI18n.KeyNotFoundError(), name)
	}
}
