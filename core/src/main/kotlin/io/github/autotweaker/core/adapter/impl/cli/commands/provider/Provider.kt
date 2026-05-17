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

package io.github.autotweaker.core.adapter.impl.cli.commands.provider

import com.google.auto.service.AutoService
import io.github.autotweaker.api.CoreAPI
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import io.github.autotweaker.core.adapter.impl.cli.Param
import io.github.autotweaker.core.adapter.impl.cli.Request
import io.github.autotweaker.core.adapter.impl.cli.Syntax
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@AutoService(Command::class)
class Provider : Command {
	lateinit var core: CoreAPI
	
	override val name = "prov"
	override val description get() = I18n.get("prov.desc")
	override val syntax = Syntax.xor(
		Syntax.leaf(Param.Flag("list", I18n.get("prov.list"))),
		Syntax.leaf(Param.Value("show", I18n.get("prov.show"), emptyList())),
		Syntax.leaf(Param.Flag("types", I18n.get("prov.types"), emptyList())),
		Syntax.leaf(Param.Value("info", I18n.get("prov.info"))),
		Syntax.all(
			Syntax.leaf(Param.Flag("add", I18n.get("prov.add")), required = true),
			Syntax.leaf(Param.Value("name", I18n.get("prov.add.name"), emptyList())),
			Syntax.leaf(Param.Value("type", I18n.get("prov.add.type"), emptyList())),
			Syntax.leaf(Param.Value("key", I18n.get("prov.add.key"), emptyList())),
			Syntax.leaf(Param.Value("url", I18n.get("prov.add.url"), emptyList())),
		),
		Syntax.all(
			Syntax.leaf(Param.Value("remove", I18n.get("prov.remove"), listOf("rm")), required = true),
			Syntax.leaf(Param.Flag("yes", I18n.get("prov.yes"))),
		),
		Syntax.leaf(Param.Value("rename", I18n.get("prov.rename"))),
		Syntax.leaf(Param.Value("update", I18n.get("prov.update"))),
	)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(request: Request, prompt: suspend (text: String, echo: Boolean) -> String): Flow<Chunk> = flow {
		val read = Read(core)
		val write = Write(core, prompt)
		if (request.has("list")) {
			emitAll(read.list().map { Chunk.Data(it) })
			emit(Chunk.Done())
			return@flow
		}
		
		if (request.has("show")) {
			val name = request.get("show") ?: error("Missing provider name")
			emitAll(read.show(name).map { Chunk.Data(it) })
			emit(Chunk.Done())
			return@flow
		}
		
		if (request.has(("types"))) {
			emitAll(read.types().map { Chunk.Data(it) })
			emit(Chunk.Done())
			return@flow
		}
		
		if (request.has("info")) {
			val name = request.get("info") ?: error("Missing provider type")
			emitAll(read.info(name).map { Chunk.Data(it) })
			emit(Chunk.Done())
			return@flow
		}
		
		if (request.has("add")) {
			val name = request.get("name")
			val type = request.get("type")
			val key = request.get("key")
			val url = request.get("url")
			emitAll(write.add(name, type, key, url))
			return@flow
		}
		
		if (request.has("remove")) {
			emitAll(write.remove(request.get("remove") ?: error("Missing provider name"), request.has("yes")))
			return@flow
		}
		
		emit(Chunk.Done(1))
		return@flow
	}
}
