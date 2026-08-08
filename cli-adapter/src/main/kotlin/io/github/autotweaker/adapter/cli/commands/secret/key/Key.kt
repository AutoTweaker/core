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

import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.secret.SecretI18n
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n

class Key : Command, Traceable {
	override val name = "key"
	override val description = i18n(KeyI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		flag("list", SecretI18n.ParamList())
		value("add", SecretI18n.ParamAdd())
		value("remove", SecretI18n.ParamRemove()) {
			aliases("rm")
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		with(KeyManager(core)) {
			handleFlag("list") {
				list()
			}
			handleValue("add") {
				add(it)
			}
			handleValue("remove") {
				remove(it)
			}
		}
		
		error(SecretI18n.InvalidArg())
	}
}
