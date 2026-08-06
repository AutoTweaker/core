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
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab

@AutoService(Command::class)
class Trace : Command {
	override val name = "trace"
	override val description = i18n(TraceI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		flag("list", TraceI18n.ListDesc())
		all {
			flag("show", TraceI18n.Show())
			positional("origin", TraceI18n.Origin())
			positional("namespace", TraceI18n.Namespace())
			positional("range", TraceI18n.Range())
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("list") {
			core.trace.origins().forEach { origin ->
				out(origin) { newline = false }
				core.trace.namespaces(origin).forEach {
					val count = core.trace.count(origin, it)
					out("[$it:$count]") { newline = false }
				}
				ln()
			}
		}
		
		handleFlag("show") {
			val origin = getPositional(0)
			val namespace = getPositional(1).toKebab()
			val range = getPositional(2).split("-", limit = 2).map { it.trim() }
			val from = range[0].toUIntOrNull() ?: error(TraceI18n.InvalidValue())
			val to = range.getOrNull(1)?.toUIntOrNull() ?: from
			val timestamp = core.trace.entries(origin, namespace, from..to)
			timestamp.forEach {
				val content = core.trace.get(origin, namespace, it)
				out("<timestamp>$it</timestamp>$content")
			}
		}
		
		done(1)
	}
}
