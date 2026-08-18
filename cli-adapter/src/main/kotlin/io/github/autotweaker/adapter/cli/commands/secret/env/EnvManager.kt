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

package io.github.autotweaker.adapter.cli.commands.secret.env

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.config.EnvType

class EnvManager(
	private val core: CoreAPI
) : I18nable {
	suspend fun Console.list(type: EnvType) {
		core.config.listEnv(type).forEach {
			out(it)
		}
	}
	
	suspend fun Console.add(type: EnvType, name: String) {
		val value = promptOrStdin(EnvI18n.PromptInputEnv(), name, echo = false)
		core.config.setEnv(type, name, value)
	}
	
	suspend fun Console.get(type: EnvType, name: String) {
		val value = core.config.getEnv(type, name)
			?: error(EnvI18n.EnvNotFoundError(), name)
		out(value)
	}
	
	suspend fun Console.remove(type: EnvType, name: String) {
		if (!core.config.removeEnv(type, name))
			error(EnvI18n.EnvNotFoundError(), name)
	}
}
