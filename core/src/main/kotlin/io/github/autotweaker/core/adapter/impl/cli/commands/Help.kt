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
import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import io.github.autotweaker.core.adapter.impl.cli.Param
import io.github.autotweaker.core.adapter.impl.cli.Request
import io.github.autotweaker.core.adapter.impl.cli.Syntax
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class Help(private val loaded: List<Command>) : Command {
	override val name = "help"
	override val description get() = I18n.get("cmd.help.desc")
	override val syntax
		get() = Syntax.all(
			Syntax.leaf(Param.Positional("command", I18n.get("cmd.help.param.command"))),
			required = false,
		)
	
	private val all: List<Command> get() = loaded + this
	
	override fun handle(request: Request, prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val target = request.positional.firstOrNull()
		if (target != null) {
			val cmd = all.find { it.name == target }
			if (cmd == null) {
				emit(Chunk.Data(I18n.get("cmd.unknown", target), Chunk.Channel.STDERR))
				emit(Chunk.Done(1))
				return@flow
			}
			emitAll(formatDetail(cmd))
			emit(Chunk.Done())
			return@flow
		}
		emit(Chunk.Data(I18n.get("cmd.available")))
		for (cmd in all.sortedBy { it.name }) {
			emit(Chunk.Data("  ${cmd.name}  —  ${cmd.description}"))
		}
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("cmd.help_hint", request.prog)))
		emit(Chunk.Done())
	}
	
	private fun formatDetail(cmd: Command): Flow<Chunk> = flow {
		emit(Chunk.Data("${cmd.name}  —  ${cmd.description}"))
		val lines = formatSyntax(cmd.syntax)
		if (lines.isNotEmpty()) {
			emit(Chunk.Data(""))
			emit(Chunk.Data(I18n.get("cmd.params")))
			for (line in lines) {
				emit(Chunk.Data(line))
			}
		}
	}
	
	companion object {
		fun formatSyntax(root: Syntax): List<String> {
			val lines = mutableListOf<String>()
			when (root) {
				is Syntax.Leaf -> appendParam(root, lines, "")
				is Syntax.All -> renderChildren(root.children, lines, "", root.required)
				is Syntax.Xor -> {
					lines.add(I18n.get("syntax.xor"))
					renderChildren(root.children, lines, "  ", root.required)
				}
			}
			return lines
		}
		
		private fun renderTree(node: Syntax, lines: MutableList<String>, isLast: Boolean, bars: String) {
			val connector = if (isLast) "└── " else "├── "
			
			when (node) {
				is Syntax.Leaf -> appendParam(node, lines, "$bars$connector")
				is Syntax.All -> renderChildren(node.children, lines, bars, node.required)
				is Syntax.Xor -> {
					lines.add("$bars$connector${I18n.get("syntax.xor")}")
					val childBars = bars + if (isLast) "    " else "│   "
					renderChildren(node.children, lines, childBars, node.required)
				}
			}
		}
		
		private fun renderChildren(
			children: List<Syntax>, lines: MutableList<String>, bars: String, required: Boolean
		) {
			for ((i, child) in children.withIndex()) {
				renderTree(child, lines, i == children.lastIndex, bars)
			}
			if (!required && lines.isNotEmpty()) {
				lines[lines.lastIndex] += " ${I18n.get("param.optional")}"
			}
		}
		
		private fun appendParam(node: Syntax.Leaf, lines: MutableList<String>, prefix: String) {
			val p = node.param
			val req = if (node.required) " ${I18n.get("param.required")}" else ""
			lines.add("$prefix${p.format()}  —  ${p.description}$req")
		}
	}
}
