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
	
	private data class ContentNode(
		val text: String, val children: List<ContentNode> = emptyList()
	)
	
	private fun Syntax.toContent(ancestorOptional: Boolean = false): List<ContentNode> {
		val isOptional = ancestorOptional || !required
		return when (this) {
			is Syntax.Leaf -> {
				val opt = if (isOptional) " ${i18n.get(HelpI18n.ParamOptional())}" else ""
				listOf(ContentNode("${param.format()}  —  ${param.description}$opt"))
			}
			
			is Syntax.Xor -> {
				val opt = if (isOptional) " ${i18n.get(HelpI18n.ParamOptional())}" else ""
				val labelText = i18n.get(HelpI18n.SyntaxXorLabel()) + opt
				listOf(ContentNode(labelText, children = children.flatMap { it.toContent(isOptional) }))
			}
			
			is Syntax.All -> {
				val allChildNodesLists = children.map { it.toContent(isOptional) }.filter { it.isNotEmpty() }
				if (allChildNodesLists.isEmpty()) return emptyList()
				
				val firstList = allChildNodesLists.first()
				val head = firstList.first()
				
				val remainingFromFirst = firstList.drop(1)
				val subsequentNodes = allChildNodesLists.drop(1).flatten()
				
				listOf(head.copy(children = head.children + remainingFromFirst + subsequentNodes))
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
