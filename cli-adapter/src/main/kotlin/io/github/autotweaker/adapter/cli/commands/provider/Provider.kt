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

package io.github.autotweaker.adapter.cli.commands.provider

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n

@AutoService(Command::class)
class Provider : Command {
	lateinit var core: CoreAPI
	
	override val name = "prov"
	override val description = i18n(ProvI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		flag("list", ProvI18n.List())
		value("show", ProvI18n.Show()) { aliases() }
		flag("types", ProvI18n.Types()) { aliases() }
		value("info", ProvI18n.Info())
		all {
			flag("add", ProvI18n.Add())
			value("name", ProvI18n.AddName()) {
				required = false
				aliases()
			}
			value("type", ProvI18n.AddType()) {
				required = false
				aliases()
			}
			value("key", ProvI18n.AddKey()) {
				required = false
				aliases()
			}
			value("url", ProvI18n.AddUrl()) {
				required = false
				aliases()
			}
		}
		all {
			value("remove", ProvI18n.Remove()) {
				aliases("rm")
			}
			flag("yes", ProvI18n.Yes()) {
				required = false
			}
		}
		all {
			value("rename", ProvI18n.Rename())
			positional("new", ProvI18n.NewName())
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		val queries = ProviderQueries(core)
		val commands = ProviderCommands(core)
		handleFlag("list") {
			with(queries) {
				list()
			}
		}
		
		handleValue("show") {
			with(queries) {
				show(it)
			}
		}
		
		handleFlag("types") {
			with(queries) {
				types()
			}
		}
		
		handleValue("info") {
			with(queries) {
				info(it)
			}
		}
		
		handleFlag("add") {
			with(commands) {
				add(
					getValueOrNull("name"),
					getValueOrNull("type"),
					getValueOrNull("key"),
					getValueOrNull("url"),
				)
			}
		}
		
		handleValue("remove") {
			with(commands) {
				remove(it, hasArg("yes"))
			}
		}
		
		handleValue("rename") {
			with(commands) {
				rename(it, getPositional(0))
			}
		}
		
		done(1)
	}
}
