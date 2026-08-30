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
import io.github.autotweaker.adapter.cli.commands.session.usage.SessionUsage
import io.github.autotweaker.adapter.cli.syntax.ALL
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.AgentAPI
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.ShortIdMapper
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.session.diff
import io.github.autotweaker.api.base.unifiedDiff
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.agent.AgentContextIndex.Turn
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.UiBlock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

@AutoService(Command::class)
class Session : Command, Traceable, Loggable {
	override val name = "session"
	override val description = i18n(SessionI18n.Desc())
	override val syntax = buildSyntax(ALL) {
		value("workspace", SessionI18n.WorkspaceParam()) { required = false }
		xor {
			flag("list", SessionI18n.ListFlag())
			all {
				flag("new", SessionI18n.NewFlag())
				positional("message", SessionI18n.MessageParam()) { required = false }
			}
			all {
				value("send", SessionI18n.SendFlag())
				positional("message", SessionI18n.MessageParam()) { required = false }
			}
			all {
				xor {
					value("approve", SessionI18n.ApproveFlag())
					value("reject", SessionI18n.RejectFlag())
				}
				flag("all", SessionI18n.AllFlag()) { required = false; aliases() }
				positional("reason", SessionI18n.ReasonParam()) { required = false }
			}
			value("yolo", SessionI18n.YoloFlag()) { aliases() }
			all {
				value("view", SessionI18n.ViewFlag())
				flag("follow", SessionI18n.FollowFlag()) { required = false }
			}
			value("update-model", SessionI18n.UpdateModelFlag())
			value("status", SessionI18n.StatusFlag()) { aliases() }
			value("delete", SessionI18n.DeleteFlag())
		}
	}
	override val children = listOf(SessionModel(), SessionUsage())
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		ModelManager.init(core)
		val workspace = getValueOrNull("workspace").let { workspaceName ->
			val list = core.workspace.list()
			list.find { it.meta.displayName == workspaceName }
				?: list.find { it.meta.path == cwd }
				?: core.workspace.get(core.workspace.default)
				?: unreachable()
		}
		
		if (core.pathResolver.inContainer(workspace.meta.path))
			err(SessionI18n.ContainerWorkspaceFormat(), workspace.meta.displayName, workspace.meta.path) { white() }
		else err(SessionI18n.WorkspaceFormat(), workspace.meta.displayName, workspace.meta.path) { white() }
		handleFlag("list") {
			val ids = workspace.sessionIds
			if (ids.isEmpty()) error(SessionI18n.NoSessions())
			
			ids.chunked(100).forEach { chunk ->
				val unorderedSessions = core.persistence.loadData(chunk.toSet())
				val orderMap = chunk.withIndex().associate { it.value to it.index }
				val sessions = unorderedSessions.sortedBy { item ->
					orderMap[item.id] ?: Int.MAX_VALUE
				}
				sessions.forEachBetween(
					action = { session ->
						val agent = core.persistence.loadAgent(session.agentIndex.main.id)
						out(SessionI18n.SessionId(), ShortIdMapper.shortString(session.id))
						out(SessionI18n.SessionTitle(), session.title)
						out(SessionI18n.MessageCount(), agent?.context?.index?.ids()?.count() ?: 0)
					},
					between = { out(LINE) }
				)
			}
			err(SessionI18n.IdRestartWarning()) { yellow() }
		}
		handleFlag("new") {
			val newId = core.session.create(workspace.id, getConfig())
			out(SessionI18n.SessionCreated(), ShortIdMapper.shortString(newId)) { green() }
			val msg = getPositionalOrNull(0) ?: readAll() ?: done()
			val agent = core.session.getHandle(newId).mainAgent()
			send(agent, msg)
		}
		handleValue("delete") {
			val id = sessionId(it, workspace)
			val success = core.session.delete(id)
			if (!success) error(SessionI18n.SessionNotFound(), it)
			else out(SessionI18n.SessionDeleted(), it) { green() }
		}
		handleValue("view") {
			val session = core.session.getHandle(sessionId(it, workspace))
			val agent = session.mainAgent()
			out(SessionI18n.SessionEntered(), it) { green() }
			ln()
			if (hasArg("follow"))
				view(core, agent)
			else printInitial(core, agent.context.value)
		}
		handleValue("send") { value ->
			val id = sessionId(value, workspace)
			val agent = core.session.getHandle(id).mainAgent()
			var message = getPositionalOrNull(0) ?: readAll()
			if (message.isNullOrBlank()) message = prompt(">")
			send(agent, message)
		}
		handleValue("approve") {
			approve(it, workspace, core)
		}
		handleValue("reject") {
			approve(it, workspace, core)
		}
		handleValue("yolo") { value ->
			if (!core.pathResolver.inContainer(workspace.meta.path))
				error(SessionI18n.YoloContainerOnly())
			
			val id = sessionId(value, workspace)
			val session = core.session.getHandle(id)
			val agent = session.mainAgent()
			out(SessionI18n.YoloStart(), value) { yellow() }
			agent.context.collect {
				agent.context.value.index.currentRound?.pendingToolCalls?.forEach { call ->
					val msg = call.loadMessage<AgentMessage.Tool.Call>(core) ?: return@collect
					agent.approve(
						ToolApprove(
							msg.callId,
						)
					)
					out(SessionI18n.CallApproved()) { green() }
					msg.printMsg()
				}
			}
		}
		handleValue("status") { value ->
			val session = core.session.getHandle(sessionId(value, workspace))
			out(SessionI18n.SessionId(), value)
			out(SessionI18n.SessionTitle(), session.data.value.title)
			session.agents.sortedBy { it.name }.forEach { agent ->
				out(SessionI18n.AgentName(), agent.name)
				out(SessionI18n.CurrentStatus(), agent.status.value)
				out(SessionI18n.MessageCount(), agent.context.value.index.ids().count())
				agent.context.value.droppedMessages?.let {
					if (it.isNotEmpty()) out(SessionI18n.DroppedMessages(), it.count())
				}
				out(SessionI18n.Reasoning(), agent.model.reasoning)
				agent.toolCalling.value?.second?.print()
				out(SessionI18n.ActiveTools(), agent.activeTools.value.joinToString())
			}
		}
		handleValue("update-model") {
			core.session.getHandle(sessionId(it, workspace)).mainAgent().setModel(getConfig())
			out(SessionI18n.ModelUpdated(), it)
		}
		
		done(1)
	}
	
	private suspend fun Console.send(agent: AgentAPI, message: String) {
		val deferred = agent.send(
			MessageContent(
				injections = listOf(
					ContextInjection(
						"user_environment",
						i18n(SessionI18n.UserEnvironment())
					)
				),
				content = message.toContentPart()
			)
		)
		err(SessionI18n.MessageSent()) { white() }
		val msg = deferred.await()?.second
		msg ?: error(SessionI18n.MessageDropped())
		out(SessionI18n.MessageReceived()) { green() }
		msg.content?.filterIsInstance<ContentPart.Text>()?.forEach {
			out("> ${it.content}")
		}
	}
	
	private suspend fun Console.approve(id: String, workspace: WorkspaceData, core: CoreAPI) {
		val agent = core.session.getHandle(sessionId(id, workspace)).mainAgent()
		
		val call = agent.context.value.index.currentRound?.pendingToolCalls?.let { calls ->
			if (hasArg("all")) calls else calls.firstOrNull()?.let { first -> listOf(first) }
		}
		val msg = call?.mapNotNullTo(mutableListOf()) {
			it.loadMessage<AgentMessage.Tool.Call>(core)
		}
		if (msg.isNullOrEmpty()) error(SessionI18n.NoPendingCalls())
		val approved = when {
			hasArg("approve") -> true
			hasArg("reject") -> false
			else -> unreachable()
		}
		
		suspend fun AgentMessage.Tool.Call.approve(reason: String?) {
			agent.approve(
				ToolApprove(
					callId,
					reason,
					approved
				)
			)
			
			out(
				if (approved) SessionI18n.CallApproved() else SessionI18n.CallRejected(),
			) { green() }
			printMsg()
		}
		
		msg.removeFirst().approve(getPositionalOrNull(0))
		
		msg.forEach {
			it.approve(null)
		}
		val processed = call.toSet()
		val pending = agent.context.value.index.currentRound?.pendingToolCalls?.mapNotNull {
			if (it in processed) return@mapNotNull null
			it.loadMessage<AgentMessage.Tool.Call>(core)
		}?.orNull()
		
		if (pending != null) out(SessionI18n.RemainingRequests())
		pending?.forEach {
			it.printMsg()
		}
	}
	
	private suspend fun Console.sessionId(id: String, workspace: WorkspaceData): UUID {
		val uuid = trace.catching {
			ShortIdMapper.uuid(id.trim())
		}.getOrNull()
		val session = if (uuid in workspace.sessionIds) uuid else null
		return session ?: error(SessionI18n.SessionIdInvalid(), id)
	}
	
	
	private suspend fun Console.view(core: CoreAPI, agent: AgentAPI) =
		coroutineScope {
			val outputLock = ReentrantMutex()
			launch {
				agent.status.collectLatest { state ->
					if (state == AgentStatus.THINKING) outputLock.withLock {
						altScreen {
							out(SessionI18n.Thinking()) { white() }
							ln()
							var lastReasoning: String? = null
							agent.output.collect { output ->
								if (output is AgentOutput.LlmDelta) {
									output.reasoningContent?.let {
										lastReasoning = it.orNull()
										out(it) {
											newline = false
											white(); italic()
										}
									}
									
									output.content?.let {
										if (lastReasoning != null) {
											if (lastReasoning?.endsWith('\n') == false) {
												ln()
											}
											ln()
											lastReasoning = null
										}
										out(it) {
											newline = false
										}
									}
								}
							}
						}
					}
				}
			}
			launch {
				var lastest: AgentContext? = null
				val showedMsg = mutableSetOf<UUID>()
				suspend fun UUID.ifNew(block: suspend UUID.() -> Unit) {
					if (this !in showedMsg) {
						showedMsg.add(this)
						block()
					}
				}
				agent.context.collect { new ->
					agent.status.first { it != AgentStatus.THINKING }
					outputLock.withLock {
						val old = lastest
						lastest = new
						if (old == null) {
							printInitial(core, new)
							showedMsg.addAll(new.index.ids())
							return@withLock
						}
						val diff = old diff new
						diff ?: return@withLock
						diff.addedMessages()?.loadToCache(core)
						with(core) {
							suspend fun List<Turn>.printIfNew() = forEach { turn ->
								turn.assistantMessage.ifNew { printMsg<AgentMessage.Assistant>() }
								turn.tools.forEach { tool ->
									tool.result.ifNew { printMsg<AgentMessage.Tool.Result>() }
								}
							}
							diff.addedHistoryRounds()?.forEach { round ->
								round.userMessage.ifNew { printMsg<AgentMessage.User>() }
								round.turns?.printIfNew()
								round.finalAssistantMessage?.ifNew { printMsg<AgentMessage.Assistant>() }
							}
							diff.startedRound()?.let { current ->
								current.userMessage.ifNew { printMsg<AgentMessage.User>() }
								current.turns?.printIfNew()
								current.assistantMessage?.ifNew { printMsg<AgentMessage.Assistant>() }
								current.finishedToolCalls?.forEach {
									it.result.ifNew { printMsg<AgentMessage.Tool.Result>() }
								}
								current.pendingToolCalls?.forEach {
									it.ifNew { printMsg<AgentMessage.Tool.Call>() }
								}
							}
							diff.updatedCurrent()?.let {
								it.addedTurns()?.printIfNew()
								it.newAssistantMessage()?.ifNew {
									printMsg<AgentMessage.Assistant>()
								}
								it.addedFinishedCalls()?.forEach { tool ->
									tool.result.ifNew { printMsg<AgentMessage.Tool.Result>() }
								}
								it.addedPendingCalls()?.forEach { tool ->
									tool.ifNew { printMsg<AgentMessage.Tool.Call>() }
								}
							}
						}
					}
				}
			}
			launch {
				agent.toolCalling.collect { call ->
					if (call == null) return@collect
					outputLock.withLock {
						call.second.print()
					}
				}
			}
			launch {
				agent.output.collect { output ->
					when (output) {
						is AgentOutput.Error -> outputLock.withLock {
							err(SessionI18n.AgentError(), output.message) { red() }
						}
						
						is AgentOutput.LlmError ->
							err(
								SessionI18n.LlmError(),
								buildString {
									if (allNull(output.content, output.statusCode, output.exception)) {
										append(i18n(SessionI18n.UnknownException()))
										return@buildString
									}
									output.statusCode?.let { append("[HTTP $it]") }
									output.content?.let { append(it) }
									output.exception?.let { append(it.message()) }
								}
							) { yellow() }
						
						else -> {}
					}
				}
			}
		}
	
	private suspend fun Console.printInitial(core: CoreAPI, context: AgentContext) = with(core) {
		val ids = context.index.currentRound?.ids().orEmpty() +
				context.index.historyRounds?.flatMap { it.ids() }.orEmpty()
		ids.loadToCache(core)
		
		suspend fun List<Turn.Tool>.print() = forEach { tool ->
			tool.result.printMsg<AgentMessage.Tool.Result>()
		}
		
		suspend fun List<Turn>.print() = forEach { turn ->
			turn.assistantMessage.printMsg<AgentMessage.Assistant>()
			turn.tools.print()
		}
		context.index.historyRounds?.forEach { round ->
			round.userMessage.printMsg<AgentMessage.User>()
			round.turns?.print()
			round.finalAssistantMessage?.printMsg<AgentMessage.Assistant>()
		}
		context.index.currentRound?.let { current ->
			current.userMessage.printMsg<AgentMessage.User>()
			current.turns?.print()
			current.assistantMessage?.printMsg<AgentMessage.Assistant>()
			current.finishedToolCalls?.print()
			current.pendingToolCalls?.forEach { call ->
				call.printMsg<AgentMessage.Tool.Call>()
			}
		}
	}
	
	context(c: Console, core: CoreAPI)
	private suspend inline fun <reified T : AgentMessage> UUID.printMsg() = with(c) {
		val msg = loadMessage<T>(core)
		if (msg == null) {
			out(SessionI18n.CorruptMessage(), this) { red() }
			return@with
		}
		msg.printMsg()
	}
	
	context(c: Console)
	private suspend fun AgentMessage.printMsg() = with(c) {
		when (this@printMsg) {
			is AgentMessage.Assistant -> {
				reasoning?.let {
					out(it) {
						white(); italic()
					}
				}
				
				if (reasoning?.endsWith('\n') == false) ln()
				content?.let {
					out(it)
				}
				usage?.let {
					out(
						SessionI18n.Usage(),
						it.promptTokens,
						it.completionTokens,
						it.cacheHitRateStr
					) {
						cyan()
					}
				}
				ln()
			}
			
			is AgentMessage.Compact -> {}
			is AgentMessage.Tool.Call -> {
				presentation?.print()
				ln()
			}
			
			is AgentMessage.Tool.Result -> {
				presentation.print()
				ln()
			}
			
			is AgentMessage.UsageRecord -> {}
			is AgentMessage.User -> {
				content.content?.filterIsInstance<ContentPart.Text>()?.forEach {
					out("> ${it.content}")
				}
				ln()
			}
		}
	}.discard()
	
	context(c: Console)
	private suspend fun ToolPresentation.print() = with(c) {
		forEach {
			when (it) {
				is UiBlock.Text -> out(it.content) { yellow() }
				is UiBlock.Command -> out(it.command) { blue() }
				is UiBlock.Diff -> it.print()
				is UiBlock.Error -> it.content.lines().let { lines ->
					lines.take(5).forEach { line ->
						out(line) { red() }
					}
					if (lines.count() > 5) out("...") { red() }
				}
				
				is UiBlock.Output -> it.content.lines().let { lines ->
					lines.take(5).forEach { line ->
						out(line)
					}
					if (lines.count() > 5) out("...")
				}
			}
		}
	}
	
	context(c: Console)
	private suspend fun UiBlock.Diff.print() = with(c) {
		unifiedDiff(oldContent, newContent)?.lines()?.forEach { line ->
			when {
				line.startsWith("@@") -> out(line) { cyan() }
				line.startsWith("+") -> out(line) { green() }
				line.startsWith("-") -> out(line) { red() }
				else -> out(line)
			}
		}
	}
	
	private val messages = mutableMapOf<UUID, AgentMessage>()
	
	private suspend inline fun <reified T : AgentMessage> UUID.loadMessage(core: CoreAPI): T? =
		getOrLoad(core) as? T
	
	private suspend fun UUID.getOrLoad(core: CoreAPI) =
		messages[this] ?: run { setOf(this).loadToCache(core); messages[this] }
	
	private suspend fun Set<UUID>.loadToCache(core: CoreAPI) =
		core.persistence.loadMessages(this).forEach {
			messages[it.id] = it
		}
}
