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

package io.github.autotweaker.adapter.cli.commands.help

import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.api.i18n.I18nService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class Help(private val loaded: List<Command>, private val i18n: I18nService) : Command {
	override val name = "help"
	override val description get() = i18n.get(HelpI18n.HelpDesc())
	override val syntax
		get() = Syntax.all(
			Syntax.leaf(Param.Positional("command", i18n.get(HelpI18n.HelpParamCommand()))),
			required = false,
		)
	
	private val all: List<Command> get() = loaded + this
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val target = request.positional.firstOrNull()
		if (target != null) {
			val cmd = all.find { it.name == target }
			if (cmd == null) {
				emit(CmdOutput.Data(i18n.get(HelpI18n.Unknown()).format(target), CmdOutput.Channel.STDERR))
				emit(CmdOutput.Done(1))
				return@flow
			}
			emitAll(formatDetail(cmd))
			emit(CmdOutput.Done())
			return@flow
		}
		emit(CmdOutput.Data(i18n.get(HelpI18n.Available())))
		for (cmd in all.sortedBy { it.name }) {
			emit(CmdOutput.Data("  ${cmd.name}  —  ${cmd.description}"))
		}
		emit(CmdOutput.Data(""))
		emit(CmdOutput.Data(i18n.get(HelpI18n.HelpHint()).format(request.prog)))
		emit(CmdOutput.Done())
	}
	
	private fun formatDetail(cmd: Command): Flow<CmdOutput> = flow {
		emit(CmdOutput.Data("${cmd.name}  —  ${cmd.description}"))
		val lines = formatSyntax(cmd.syntax)
		if (lines.isNotEmpty()) {
			emit(CmdOutput.Data(""))
			emit(CmdOutput.Data(i18n.get(HelpI18n.Params())))
			for (line in lines) {
				emit(CmdOutput.Data(line))
			}
		}
	}
	
	private sealed class ContentNode {
		data class Label(val text: String, val children: List<ContentNode>) : ContentNode()
		data class Group(val children: List<ContentNode>) : ContentNode()
		data class Leaf(val text: String) : ContentNode()
	}
	
	private fun Syntax.toContent(ancestorOptional: Boolean = false): ContentNode {
		val isOptional = ancestorOptional || when (this) {
			is Syntax.Leaf -> !required
			is Syntax.Xor -> !required
			is Syntax.All -> !required
		}
		return when (this) {
			is Syntax.Leaf -> {
				val opt = if (isOptional) " ${i18n.get(HelpI18n.ParamOptional())}" else ""
				ContentNode.Leaf("${param.format()}  —  ${param.description}$opt")
			}
			
			is Syntax.All -> ContentNode.Group(children.map { it.toContent(isOptional) })
			is Syntax.Xor -> {
				val opt = if (isOptional) " ${i18n.get(HelpI18n.ParamOptional())}" else ""
				ContentNode.Label(
					i18n.get(HelpI18n.SyntaxXorLabel()) + opt,
					children.map { it.toContent(isOptional) },
				)
			}
		}
	}
	
	private fun formatSyntax(root: Syntax): List<String> {
		return renderRoot(root.toContent())
	}
	
	private fun renderRoot(node: ContentNode): List<String> {
		return when (node) {
			is ContentNode.Label -> {
				val result = mutableListOf(node.text)
				for (i in node.children.indices) {
					result.addAll(render(node.children[i], "", i == node.children.lastIndex))
				}
				result
			}
			
			is ContentNode.Group -> {
				val result = mutableListOf<String>()
				for (i in node.children.indices) {
					result.addAll(render(node.children[i], "", i == node.children.lastIndex))
				}
				result
			}
			
			is ContentNode.Leaf -> listOf(node.text)
		}
	}
	
	private fun render(node: ContentNode, bars: String, isLast: Boolean): List<String> {
		val connector = if (isLast) "└── " else "├── "
		return when (node) {
			is ContentNode.Leaf -> listOf("$bars$connector${node.text}")
			is ContentNode.Label -> {
				val result = mutableListOf("$bars$connector${node.text}")
				val childBars = bars + if (isLast) "    " else "│   "
				for (i in node.children.indices) {
					result.addAll(render(node.children[i], childBars, i == node.children.lastIndex))
				}
				result
			}
			
			is ContentNode.Group -> {
				if (node.children.isEmpty()) return emptyList()
				val result = mutableListOf<String>()
				val hasMore = node.children.size > 1
				
				val firstIsLast = isLast && (bars.isEmpty() || !hasMore)
				result.addAll(render(node.children.first(), bars, firstIsLast))
				
				val childBars = bars + if (firstIsLast) "    " else "│   "
				for (i in 1 until node.children.size) {
					result.addAll(render(node.children[i], childBars, i == node.children.lastIndex))
				}
				result
			}
		}
	}
}
