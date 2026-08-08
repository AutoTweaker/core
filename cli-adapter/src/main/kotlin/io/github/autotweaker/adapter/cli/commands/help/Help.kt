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
import io.github.autotweaker.adapter.cli.syntax.POSITIONAL
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.adapter.cli.syntax.buildLeaf
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.unreachable

class Help(private val loaded: List<Command>) : Command {
	override val name = "help"
	override val description = i18n(HelpI18n.HelpDesc())
	override val requiresKeystore = false
	override val syntax = buildLeaf(
		POSITIONAL, "command", i18n(HelpI18n.HelpParamCommand())
	) { required = false }
	
	
	private val all: List<Command> get() = loaded + this
	
	override suspend fun Console.execute(core: CoreAPI): Nothing =
		unreachable()
	
	suspend fun Console.executePath(path: List<String>): Nothing {
		if (path.isEmpty()) {
			renderAll()
			done()
		}
		val target = findPath(path) ?: error(HelpI18n.Unknown(), path.joinToString(" "))
		renderDetail(target)
		done()
	}
	
	private fun findPath(path: List<String>): Command? {
		var current: Command? = null
		path.forEach { segment ->
			current = (current?.children ?: all).find { it.name == segment } ?: return null
		}
		return current
	}
	
	private suspend fun Console.renderAll() {
		renderList(HelpI18n.Available(), all)
		ln()
		out(HelpI18n.HelpHint(), "$APP_NAME_LOWERCASE help <command>")
	}
	
	private suspend fun Console.renderList(title: I18nDef, items: List<Command>) {
		out(title)
		for (cmd in items.sortedBy { it.name }) {
			out("  ${cmd.name}  —  ${cmd.description}")
		}
	}
	
	private suspend fun Console.renderDetail(cmd: Command) {
		out("${cmd.name}  —  ${cmd.description}")
		val lines = formatSyntax(cmd.syntax)
		if (lines.isNotEmpty()) {
			ln()
			out(HelpI18n.Params())
			lines.forEach {
				out(it)
			}
		}
		if (cmd.children.isNotEmpty()) {
			ln()
			renderList(HelpI18n.Subcommands(), cmd.children)
			ln()
			out(HelpI18n.HelpHint(), "$APP_NAME_LOWERCASE help ${cmd.name} <command>")
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
		nodes.forEach { node ->
			result.add(node.text)
			node.children.indices.forEach { i ->
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
		node.children.indices.forEach { i ->
			result.addAll(renderNode(node.children[i], childBars, i == node.children.lastIndex))
		}
		return result
	}
}
