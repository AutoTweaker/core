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
import io.github.autotweaker.adapter.cli.commands.*
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitDone
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@AutoService(Command::class)
class Provider : Command, I18nable {
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
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val queries = ProviderQueries(core)
		val commands = ProviderCommands(core, prompt)
		if (request.has("list")) {
			emitAll(queries.list().map { CmdOutput.Data(it) })
			emitDone()
			return@flow
		}
		
		if (request.has("show")) {
			val name = request.get("show") ?: error("Missing provider name")
			emitAll(queries.show(name).map { CmdOutput.Data(it) })
			emitDone()
			return@flow
		}
		
		if (request.has(("types"))) {
			emitAll(queries.types().map { CmdOutput.Data(it) })
			emitDone()
			return@flow
		}
		
		if (request.has("info")) {
			val name = request.get("info") ?: error("Missing provider type")
			emitAll(queries.info(name))
			return@flow
		}
		
		if (request.has("add")) {
			val name = request.get("name")
			val type = request.get("type")
			val key = request.get("key")
			val url = request.get("url")
			emitAll(commands.add(name, type, key, url))
			return@flow
		}
		
		if (request.has("remove")) {
			emitAll(commands.remove(request.get("remove") ?: error("Missing provider name"), request.has("yes")))
			return@flow
		}
		
		if (request.has("rename")) {
			emitAll(
				commands.rename(
					name = request.get("rename") ?: error("Missing provider name"),
					new = request.positional.first(),
				)
			)
			return@flow
		}
		
		emitDone(1)
		return@flow
	}
}
