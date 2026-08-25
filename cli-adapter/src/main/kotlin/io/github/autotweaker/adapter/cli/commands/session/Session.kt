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

package io.github.autotweaker.adapter.cli.commands.session

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.getConfig
import io.github.autotweaker.adapter.cli.commands.session.model.SessionModel
import io.github.autotweaker.adapter.cli.syntax.ALL
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.ShortIdMapper
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.session.diff
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.UiBlock
import kotlinx.coroutines.coroutineScope
import java.util.*

@AutoService(Command::class)
class Session : Command, Traceable {
	override val name = "session"
	override val description = i18n(Desc())
	override val syntax = buildSyntax(ALL) {
		value("workspace", "工作区的名称，默认当前目录下的工作区，无可用则默认工作区") { required = false }
		xor {
			flag("list", "列出工作区下的所有会话")
			flag("new", "创建并进入一个新会话")
			value("enter", "进入指定的会话")
			value("send", "通过stdin向指定的会话发送消息")
			value("view", "查看指定会话")
			value("delete", "通过id删除一个会话")
		}
	}
	override val children = listOf(SessionModel())
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		ModelManager.init(core)
		val workspace = getValueOrNull("workspace").let { workspaceName ->
			val list = core.workspace.list()
			list.find { it.meta.displayName == workspaceName }
				?: list.find { it.meta.path == cwd }
				?: core.workspace.get(core.workspace.default)
				?: unreachable()
		}
		
		err("工作区: ${workspace.meta.displayName} ('${workspace.meta.path}')") { white() }
		handleFlag("list") {
			val ids = workspace.sessionIds
			if (ids.isEmpty()) error("当前工作区没有会话")
			
			ids.chunked(100).forEach { chunk ->
				val unorderedSessions = core.persistence.loadData(chunk.toSet())
				val orderMap = chunk.withIndex().associate { it.value to it.index }
				val sessions = unorderedSessions.sortedBy { item ->
					orderMap[item.id] ?: Int.MAX_VALUE
				}
				sessions.forEachBetween(
					action = { session ->
						val agent = core.persistence.loadAgent(session.agentIndex.main.id)
						out("会话 id: ${ShortIdMapper.shortString(session.id)}")
						out("会话标题: ${session.title}")
						out("消息数量: ${agent?.context?.index?.ids()?.count() ?: 0}")
					},
					between = { out(LINE) }
				)
			}
			err("会话与 id 的对应关系将会在程序重启后失效") { yellow() }
		}
		handleFlag("new") {
			val newId = core.session.create(workspace.id, getConfig())
			out(ShortIdMapper.shortString(newId))
		}
		handleValue("delete") {
			val id = sessionId(it)
			val success = core.session.delete(id)
			if (!success) error("找不到会话 $it")
			else out("删除了会话 $it") { green() }
		}
		handleValue("view") {
			view(core, sessionId(it))
		}
		
		done(1)
	}
	
	private suspend fun Console.sessionId(id: String) = trace.catching {
		ShortIdMapper.uuid(id.trim())
	}.getOrNull() ?: error("找不到会话 $id, 程序重启后需要重新运行 list 才能生成 id")
	
	
	private suspend fun Console.view(core: CoreAPI, id: UUID) {
		val agent = core.session.getHandle(id).agents.firstOrNull() ?: done()
		coroutineScope {
			var lastest: AgentContext? = null
			agent.context.collect { new ->
				val old = lastest
				if (old == null) {
					printInitial(new)
					return@collect
				}
				lastest = new
				val diff = old diff new
			}
		}
	}
	
	private fun Console.printInitial(context: AgentContext) {
		TODO()
	}
	
	context(c: Console)
	private suspend fun AgentMessage.print() = with(c) {
		when (this@print) {
			is AgentMessage.Assistant -> {
				reasoning?.let {
					out(it) {
						white(); italic()
					}
				}
				content?.let {
					out(it)
				}
				usage?.let {
					out("输入 ${it.promptTokens} tokens | 输出 ${it.completionTokens} tokens | 缓存命中率 ${it.cacheHitRate * 100}%") {
						cyan()
					}
				}
			}
			
			is AgentMessage.Compact -> {}
			is AgentMessage.Tool.Call -> {
				presentation?.print()
			}
			
			is AgentMessage.Tool.Result -> {
				presentation.print()
			}
			
			is AgentMessage.UsageRecord -> {}
			is AgentMessage.User -> {
				content.content?.filterIsInstance<ContentPart.Text>()?.forEach {
					out("> ${it.content}")
				}
			}
		}
	}
	
	context(c: Console)
	private suspend fun ToolPresentation.print() = with(c) {
		forEach {
			when (it) {
				is UiBlock.Text -> out(it.content) { cyan() }
				is UiBlock.Command -> out(it.command) { cyan() }
				is UiBlock.Diff -> TODO("暂不可达")
				is UiBlock.Error -> out(it.content) { red() }
				is UiBlock.Output -> out(it.content)
			}
		}
	}
	
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理和进入会话"),
	)
}
