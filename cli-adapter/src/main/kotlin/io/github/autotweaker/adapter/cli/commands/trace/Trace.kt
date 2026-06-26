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

package io.github.autotweaker.adapter.cli.commands.trace

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.adapter.cli.Command
import io.github.autotweaker.adapter.cli.Param.Type
import io.github.autotweaker.adapter.cli.Request
import io.github.autotweaker.adapter.cli.Syntax
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.types.KebabId.Companion.toKebabId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Trace : Command, I18nable {
	lateinit var core: CoreAPI
	
	override val name = "trace"
	override val description get() = i18n.get(TraceI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.leaf(Type.FLAG, "list", TraceI18n.ListDesc()),
			Syntax.all(
				Syntax.leaf(Type.FLAG, "show", TraceI18n.Show()),
				Syntax.leaf(Type.POSITIONAL, "origin", TraceI18n.Origin()),
				Syntax.leaf(Type.POSITIONAL, "namespace", TraceI18n.Namespace()),
				Syntax.leaf(Type.POSITIONAL, "range", TraceI18n.Range())
			)
		)
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override fun handle(request: Request, prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> =
		flow {
			if (request.has("list")) {
				core.trace.origins().forEach { origin ->
					emit(CmdOutput.Data(origin, newline = false))
					core.trace.namespaces(origin).forEach {
						val count = core.trace.count(origin, it)
						emit(CmdOutput.Data("[$it:$count]", newline = false))
					}
					emit(CmdOutput.Data(""))
				}
				emitDone()
				return@flow
			}
			
			if (request.has("show")) {
				val origin = request.positional[0]
				val namespace = request.positional[1].toKebabId()
				val range = request.positional[2].split("-", limit = 2).map { it.trim() }
				val from = range[0].toUIntOrNull() ?: run {
					emitI18n(TraceI18n.InvalidValue())
					emitDone(1)
					return@flow
				}
				val to = range.getOrNull(1)?.toUIntOrNull() ?: from
				val timestamp = core.trace.entries(origin, namespace, from..to)
				timestamp.forEach {
					val content = core.trace.get(origin, namespace, it)
					emit(CmdOutput.Data("<timestamp>$it</timestamp>$content"))
				}
				emitDone()
				return@flow
			}
			
			emitDone(1)
		}
}
