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

package io.github.autotweaker.core.adapter.impl.cli.commands

import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Command.*
import io.github.autotweaker.core.adapter.impl.cli.ParsedRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class Help(private val commands: List<Command>) : Command {
	override val name = "help"
	override val description = "Show available commands and their usage"
	override val params = listOf(
		Param("command", "Show help for a specific command", type = ParamType.POSITIONAL),
	)
	
	override fun handle(request: ParsedRequest, prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val target = request.positional.firstOrNull()
		if (target != null) {
			val cmd = commands.find { it.name == target }
			if (cmd == null) {
				emit(Chunk.Data("Unknown command: $target\n"))
				return@flow
			}
			emitAll(formatDetail(cmd))
			return@flow
		}
		emit(Chunk.Data("Available commands:\n"))
		for (cmd in commands.sortedBy { it.name }) {
			emit(Chunk.Data("  ${cmd.name}  —  ${cmd.description}\n"))
		}
		emit(Chunk.Data("\nRun 'autotweaker help <command>' for detailed usage.\n"))
	}
	
	private fun formatDetail(cmd: Command): Flow<Chunk> = flow {
		emit(Chunk.Data("${cmd.name}  —  ${cmd.description}\n"))
		if (cmd.params.isNotEmpty()) {
			emit(Chunk.Data("\nParameters:\n"))
			for (p in cmd.params) {
				val required = if (p.required) " (required)" else ""
				emit(Chunk.Data("  ${formatParam(p)}  —  ${p.description}$required\n"))
			}
		}
	}
	
	companion object {
		fun formatParam(p: Param): String = p.type.format(p.name)
	}
}