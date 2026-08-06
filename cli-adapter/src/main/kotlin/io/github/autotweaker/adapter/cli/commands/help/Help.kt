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

import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.Style
import io.github.autotweaker.adapter.cli.syntax.Request
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.adapter.cli.syntax.SyntaxLeafBuilder
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.i18n

class Help(private val loaded: List<Command>) : Command, I18nable {
	override val name = "help"
	override val description = i18n(HelpI18n.HelpDesc())
	override val syntax =
		SyntaxLeafBuilder(
			"command", i18n(HelpI18n.HelpParamCommand())
		).apply {
			required = false
		}.toPositional()
	
	
	private val all: List<Command> get() = loaded + this
	
	override suspend fun Console.render(request: Request) {
		val target = request.positional.firstOrNull()
		if (target != null) {
			val cmd = all.find { it.name == target }
			if (cmd == null) {
				err(i18n(HelpI18n.Unknown(), target), Style.RED)
				done(1)
			}
			renderDetail(cmd)
			done()
		}
		out(i18n(HelpI18n.Available()))
		for (cmd in all.sortedBy { it.name }) {
			out("  ${cmd.name}  —  ${cmd.description}")
		}
		ln()
		out(i18n(HelpI18n.HelpHint(), request.prog))
		done()
	}
	
	private suspend fun Console.renderDetail(cmd: Command) {
		out("${cmd.name}  —  ${cmd.description}")
		val lines = formatSyntax(cmd.syntax)
		if (lines.isNotEmpty()) {
			ln()
			out(i18n(HelpI18n.Params()))
			for (line in lines) {
				out(line)
			}
		}
	}
	
	private data class ContentNode(
		val text: String, val children: List<ContentNode> = emptyList()
	)
	
	private fun Syntax.toContent(ancestorOptional: Boolean = false): List<ContentNode> {
		val isOptional = ancestorOptional || !required
		return when (this) {
			is Syntax.Leaf -> {
				val opt = if (isOptional) " ${i18n(HelpI18n.ParamOptional())}" else ""
				listOf(ContentNode("${param.format()}  —  ${param.description}$opt"))
			}
			
			is Syntax.Xor -> {
				val opt = if (isOptional) " ${i18n(HelpI18n.ParamOptional())}" else ""
				val labelText = i18n(HelpI18n.SyntaxXorLabel()) + opt
				listOf(ContentNode(labelText, children = children.flatMap { it.toContent(isOptional) }))
			}
			
			is Syntax.All -> {
				val childNodes = children.flatMap { it.toContent(isOptional) }
				if (childNodes.isEmpty()) return emptyList()
				
				listOf(ContentNode("◉", children = childNodes))
			}
		}
	}
	
	private fun formatSyntax(root: Syntax): List<String> {
		val nodes = root.toContent()
		val result = mutableListOf<String>()
		for (node in nodes) {
			result.add(node.text)
			for (i in node.children.indices) {
				result.addAll(renderNode(node.children[i], "", i == node.children.lastIndex))
			}
		}
		return result
	}
	
	private fun renderNode(node: ContentNode, bars: String, isLast: Boolean): List<String> {
		val result = mutableListOf<String>()
		val connector = if (isLast) "└── " else "├── "
		result.add("$bars$connector${node.text}")
		
		val childBars = bars + if (isLast) "    " else "│   "
		for (i in node.children.indices) {
			result.addAll(renderNode(node.children[i], childBars, i == node.children.lastIndex))
		}
		return result
	}
}
