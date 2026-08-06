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

package io.github.autotweaker.adapter.cli.commands.secret

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.config.CoreConfig

class KeyManager(
	private val core: CoreAPI
) : I18nable {
	suspend fun Console.list() {
		core.config.listApiKey().forEach { out(it) }
	}
	
	suspend fun Console.add(name: String) {
		if (name.isBlank()) error(SecretI18n.EmptyNameError())
		
		val key = secret(SecretI18n.PromptInputApiKey())
		
		if (key.isBlank()) error(SecretI18n.EmptyKeyError())
		if (core.config.listApiKey().any { it == name }) error(SecretI18n.KeyExistsError(), name)
		
		core.config.addApiKey(
			CoreConfig.ProviderConfig.ApiKey(
				name, key
			)
		)
	}
	
	suspend fun Console.remove(name: String) {
		if (!core.config.removeApiKey(name)) error(SecretI18n.KeyNotFoundError(), name)
	}
}
