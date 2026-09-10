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
import com.ibm.icu.text.DateFormat
import com.ibm.icu.util.ULocale
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.getConfig
import io.github.autotweaker.adapter.cli.commands.session.model.SessionModel
import io.github.autotweaker.adapter.cli.commands.session.usage.SessionUsage
import io.github.autotweaker.adapter.cli.syntax.ALL
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.Agent
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.adapter.Session
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.ShortIdMapper
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.session.diff
import io.github.autotweaker.api.base.unifiedDiff
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.agent.AgentContextIndex.Turn
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.session.SessionSort
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.UiBlock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@AutoService(Command::class)
class SessionCmd : Command, Traceable, Loggable {
	override val name = "session"
	override val description = i18n(SessionI18n.Desc())
	override val syntax = buildSyntax(ALL) {
		value("workspace", SessionI18n.Workspace()) { required = false }
		xor {
			all {
				flag("list", SessionI18n.List())
				value("number", SessionI18n.Number()) { required = false }
			}
			all {
				flag("new", SessionI18n.New()) { aliases("create", "c") }
				positional("message", SessionI18n.Message()) { required = false }
			}
			all {
				value("send", SessionI18n.Send())
				positional("message", SessionI18n.Message()) { required = false }
			}
			all {
				value("search", SessionI18n.Search()) { aliases() }
				value("limit", SessionI18n.SearchLimit()) { aliases(); required = false }
				xor {
					flag("user", SessionI18n.SearchUser()) { aliases() }
					flag("assistant", SessionI18n.SearchAssistant()) { aliases() }
					flag("tool-call", SessionI18n.SearchToolCall()) { aliases() }
					flag("tool-result", SessionI18n.SearchToolResult()) { aliases() }
					flag("summary", SessionI18n.SearchSummary()) { aliases() }
					required = false
				}
			}
			value("pause", SessionI18n.Pause())
			value("stop", SessionI18n.Stop()) { aliases() }
			value("compact", SessionI18n.Compact()) { aliases() }
			value("cancel-compact", SessionI18n.CancelCompact()) { aliases() }
			value("cancel-tool", SessionI18n.CancelTool()) { aliases() }
			all {
				xor {
					value("approve", SessionI18n.Approve())
					value("reject", SessionI18n.Reject())
				}
				flag("all", SessionI18n.All()) { required = false; aliases() }
				positional("reason", SessionI18n.Reason()) { required = false }
			}
			value("yolo", SessionI18n.Yolo()) { aliases() }
			all {
				value("view", SessionI18n.View())
				flag("follow", SessionI18n.Follow()) { required = false }
			}
			value("update-model", SessionI18n.UpdateModel())
			value("status", SessionI18n.Status()) { aliases() }
			value("delete", SessionI18n.Delete())
		}
	}
	override val children = listOf(SessionModel(), SessionUsage())
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		ModelManager.init(core)
		val workspace = getValueOrNull("workspace").let { workspaceName ->
			val list = core.workspace.list()
			list.find { it.displayName == workspaceName }
				?: list.find { it.path == cwd }
				?: core.workspace.get(core.workspace.default)
				?: unreachable()
		}
		
		if (core.pathResolver.inContainer(workspace.path))
			err(SessionI18n.ContainerWorkspaceFormat(), workspace.displayName, workspace.path) { white() }
		else err(SessionI18n.WorkspaceFormat(), workspace.displayName, workspace.path) { white() }
		handleFlag("list") {
			val limit = getValueOrNull("number")?.toIntOrNull() ?: 20
			
			core.persistence.loadSession(
				workspace.id, SessionSort.LAST_ACCESS_TIME, limit, null
			).ifEmpty { error(SessionI18n.NoSessions()) }.reversed().forEachBetween(
				action = { session ->
					val agent = core.persistence.loadAgent(session.agentIndex.main.id)
					out(SessionI18n.SessionId(), ShortIdMapper.shortString(session.id))
					out(SessionI18n.SessionTitle(), session.title)
					out(SessionI18n.CreationTime(), session.creationTime.timeString())
					out(SessionI18n.LastAccessTime(), session.lastAccessTime.timeString())
					out(SessionI18n.MessageCount(), agent?.context?.index?.ids()?.count() ?: 0)
				},
				between = { out(LINE) }
			)
			err(SessionI18n.IdRestartWarning()) { yellow() }
		}
		handleFlag("new") {
			val newId = core.session.create(workspace.id, getConfig())
			out(SessionI18n.SessionCreated(), ShortIdMapper.shortString(newId)) { green() }
			val msg = getPositionalOrNull(0) ?: readAll() ?: done()
			val agent = core.session.restore(newId).mainAgent()
			send(agent, msg)
		}
		handleValue("delete") {
			val id = sessionId(it, workspace)
			val success = core.session.delete(id)
			if (!success) error(SessionI18n.SessionNotFound(), it)
			else out(SessionI18n.SessionDeleted(), it) { green() }
		}
		handleValue("view") {
			val session = core.session.restore(sessionId(it, workspace))
			val agent = session.mainAgent()
			out(SessionI18n.SessionEntered(), it) { green() }
			ln()
			if (hasArg("follow"))
				view(core, agent)
			else printInitial(core, agent.context.value)
		}
		handleValue("send") { value ->
			val id = sessionId(value, workspace)
			val agent = core.session.restore(id).mainAgent()
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
			if (!core.pathResolver.inContainer(workspace.path))
				error(SessionI18n.YoloContainerOnly())
			
			val id = sessionId(value, workspace)
			val session = core.session.restore(id)
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
			val session = core.session.restore(sessionId(value, workspace))
			out(SessionI18n.SessionId(), value)
			out(SessionI18n.SessionTitle(), session.title.value)
			out(SessionI18n.CreationTime(), session.creationTime.timeString())
			val agent = session.mainAgent()
			out(SessionI18n.AgentName(), agent.name)
			out(SessionI18n.CurrentStatus(), agent.status.value) { newline = false }
			if (agent.compacting.value) {
				out(SPACE.toString()) { newline = false }
				out(SessionI18n.Compacting())
			} else ln()
			out(SessionI18n.MessageCount(), agent.context.value.index.ids().count())
			agent.context.value.droppedMessages?.let {
				if (it.isNotEmpty()) out(SessionI18n.DroppedMessages(), it.count())
			}
			out(SessionI18n.Reasoning(), agent.model.reasoning)
			agent.toolCalling.value?.second?.print()
			out(SessionI18n.ActiveTools(), agent.activeTools.value.joinToString())
		}
		suspend fun agent(session: String): Agent = core.session.restore(sessionId(session, workspace)).mainAgent()
		handleValue("update-model") {
			agent(it).setModel(getConfig())
			out(SessionI18n.ModelUpdated(), it)
		}
		handleValue("pause") {
			agent(it).pause()
		}
		handleValue("stop") {
			agent(it).stop()
		}
		handleValue("compact") {
			agent(it).compact()
		}
		handleValue("cancel-compact") {
			agent(it).cancelCompact()
		}
		handleValue("cancel-tool") {
			agent(it).cancelTool()
		}
		handleValue("search") { query ->
			val limit = getValueOrNull("limit")?.toIntOrNull() ?: 10
			val type = when {
				hasArg("user") -> AgentMessageType.USER
				hasArg("assistant") -> AgentMessageType.ASSISTANT
				hasArg("tool-call") -> AgentMessageType.TOOL_CALL
				hasArg("tool-result") -> AgentMessageType.TOOL_RESULT
				hasArg("summary") -> AgentMessageType.COMPACT
				else -> null
			}
			val result = core.persistence.searchMessages(query, type, null, null)
			if (result.count() > limit) {
				out(SessionI18n.SearchMatches(), result.count(), limit)
				ln()
			}
			val messages = core.persistence.loadMessages(result.take(limit).toSet())
			messages.forEachBetween({ msg ->
				val agent = core.persistence.loadAgent(msg.origin.single()) ?: return@forEachBetween
				val session = core.persistence.loadSession(agent.sessionId) ?: return@forEachBetween
				val workspace = session.workspaceId.let { core.workspace.get(it) } ?: return@forEachBetween
				out(
					SessionI18n.SearchOrigin(),
					workspace.displayName,
					ShortIdMapper.shortString(session.id),
					session.title
				) { yellow() }
				out(msg.content().toString())
			}, between = { out(LINE) })
		}
		
		done(1)
	}
	
	private suspend fun Console.send(agent: Agent, message: String) {
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
		val agent = core.session.restore(sessionId(id, workspace)).mainAgent()
		
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
	
	
	private suspend fun Console.view(core: CoreAPI, agent: Agent): Nothing =
		coroutineScope {
			val outputLock = ReentrantMutex()
			launch {
				agent.status.collectLatest { state ->
					if (state == AgentStatus.THINKING) outputLock.withLock {
						altScreen {
							out("Thinking...") { white() }
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
				delay(5.milliseconds)
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
			agent.status.collect {
				if (it == AgentStatus.DEAD) done()
				if (it == AgentStatus.FAILED) done(1)
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
		context.index.compactedRounds?.toList()?.forEach { (summarizedMessage, rounds) ->
			val messages = core.persistence.loadMessages(
				rounds.flatMapTo(mutableSetOf()) { it.ids() } + summarizedMessage
			).associateBy { it.id }
			
			suspend fun UUID.printIf(test: (AgentMessage) -> Boolean) {
				val msg = messages[this]
				if (msg == null || !test(msg)) {
					out(SessionI18n.CorruptMessage(), this@printIf) { red() }
				} else msg.printMsg()
			}
			rounds.forEach { round ->
				round.userMessage.printIf { it is AgentMessage.User }
				round.turns?.forEach { turn ->
					turn.assistantMessage.printIf { it is AgentMessage.Assistant }
					turn.tools.forEach { tool ->
						tool.result.printIf { it is AgentMessage.Tool.Result }
					}
				}
				round.finalAssistantMessage?.printIf { it is AgentMessage.Assistant }
			}
			summarizedMessage.printIf { it is AgentMessage.Compact }
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
			out(SessionI18n.CorruptMessage(), this@printMsg) { red() }
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
			
			is AgentMessage.Compact -> {
				out(SessionI18n.CompactMessage()) { yellow() }
				ln()
				out(content) { white() }
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
	
	private suspend fun Session.mainAgent(): Agent = restore(agentIndex.value.main.id)
	
	private fun Instant.timeString(): String =
		DateFormat.getPatternInstance("yMdjms", ULocale.forLocale(i18n.getLanguage()))
			.format(Date.from(toJavaInstant()))
}
