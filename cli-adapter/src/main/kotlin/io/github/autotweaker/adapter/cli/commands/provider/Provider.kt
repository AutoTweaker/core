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
import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@AutoService(Command::class)
class Provider : Command {
	lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18nService()
	
	override val name = "prov"
	override val description get() = i18n.get(ProvI18n.Desc())
	override val syntax = Syntax.xor(
		Syntax.leaf(Param.Flag("list", i18n.get(ProvI18n.List()))),
		Syntax.leaf(Param.Value("show", i18n.get(ProvI18n.Show()), emptyList())),
		Syntax.leaf(Param.Flag("types", i18n.get(ProvI18n.Types()), emptyList())),
		Syntax.leaf(Param.Value("info", i18n.get(ProvI18n.Info()))),
		Syntax.all(
			Syntax.leaf(Param.Flag("add", i18n.get(ProvI18n.Add())), required = true),
			Syntax.leaf(Param.Value("name", i18n.get(ProvI18n.AddName()), emptyList())),
			Syntax.leaf(Param.Value("type", i18n.get(ProvI18n.AddType()), emptyList())),
			Syntax.leaf(Param.Value("key", i18n.get(ProvI18n.AddKey()), emptyList())),
			Syntax.leaf(Param.Value("url", i18n.get(ProvI18n.AddUrl()), emptyList())),
		),
		Syntax.all(
			Syntax.leaf(Param.Value("remove", i18n.get(ProvI18n.Remove()), listOf("rm")), required = true),
			Syntax.leaf(Param.Flag("yes", i18n.get(ProvI18n.Yes()))),
		),
		Syntax.leaf(Param.Value("rename", i18n.get(ProvI18n.Rename()))),
		Syntax.leaf(Param.Value("update", i18n.get(ProvI18n.Update()))),
	)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val queries = ProviderQueries(core)
		val commands = ProviderCommands(core, prompt)
		if (request.has("list")) {
			emitAll(queries.list().map { CmdOutput.Data(it) })
			emit(CmdOutput.Done())
			return@flow
		}
		
		if (request.has("show")) {
			val name = request.get("show") ?: error("Missing provider name")
			emitAll(queries.show(name).map { CmdOutput.Data(it) })
			emit(CmdOutput.Done())
			return@flow
		}
		
		if (request.has(("types"))) {
			emitAll(queries.types().map { CmdOutput.Data(it) })
			emit(CmdOutput.Done())
			return@flow
		}
		
		if (request.has("info")) {
			val name = request.get("info") ?: error("Missing provider type")
			emitAll(queries.info(name).map { CmdOutput.Data(it) })
			emit(CmdOutput.Done())
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
		
		emit(CmdOutput.Done(1))
		return@flow
	}
}
