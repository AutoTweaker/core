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

package io.github.autotweaker.core.domain.session.converter

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.get
import io.github.autotweaker.api.orNow
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.serialization.json.JsonNull
import java.util.*

class RuntimeContextBuilder(
	private val context: AgentContext,
	private val loadMessages: suspend (ids: Set<UUID>) -> List<AgentMessage>,
) {
	private val messages = mutableMapOf<UUID, AgentMessage>()
	private var compactedCount = 0
	private val keepCompacted = KeepCompactedRounds().get()
	private var droppedCompacted: AgentContextIndex.CompactedRounds? = null
	
	suspend operator fun invoke(): Pair<RuntimeContext, AgentContextIndex.CompactedRounds?> = context.let {
		loadAll(
			it.index.currentRound?.ids().orEmpty() +
					it.index.historyRounds?.flatMap { round -> round.ids() }.orEmpty()
		)
		RuntimeContext(
			systemPrompt = it.systemPrompt,
			injections = it.injections,
			compactedRounds = it.index.compactedRounds?.transform(),
			historyRounds = it.index.historyRounds?.map { round -> round.transform() },
			currentRound = it.index.currentRound?.transform()
		) to droppedCompacted
	}
	
	private suspend fun AgentContextIndex.CompactedRounds.transform(): RuntimeContext.CompactedRounds =
		RuntimeContext.CompactedRounds(
			compactedRounds = if (++compactedCount < keepCompacted) {
				compactedRounds?.transform()
			} else {
				droppedCompacted = compactedRounds
				null
			},
			rounds = rounds.map { it.transform() },
			summarizedMessage = summarizedMessage(summarizedMessage)
		)
	
	private suspend fun AgentContextIndex.CompletedRound.transform() = RuntimeContext.CompletedRound(
		userMessage = userMessage(userMessage),
		turns = turns?.map { it.transform() },
		finalAssistantMessage = finalAssistantMessage?.let { assistantMessage(it) }
	)
	
	private suspend fun AgentContextIndex.CurrentRound.transform() = RuntimeContext.CurrentRound(
		userMessage = userMessage(userMessage),
		turns = turns?.map { it.transform() },
		assistantMessage = assistantMessage?.let { assistantMessage(it) },
		pendingToolCalls = pendingToolCalls?.map { pendingToolCall(it) }
	)
	
	private suspend fun AgentContextIndex.Turn.transform() = RuntimeContext.Turn(
		assistantMessage = assistantMessage(assistantMessage),
		tools = tools.map { it.transform() }
	)
	
	private suspend fun AgentContextIndex.Turn.Tool.transform() = message<AgentMessage.Tool.Call>(call).let {
		RuntimeContext.Message.Tool(
			call = toolCall(call),
			callId = it?.callId.orEmpty(),
			result = toolResult(result)
		)
	}
	
	private suspend fun userMessage(id: UUID) = message<AgentMessage.User>(id).let {
		RuntimeContext.Message.User(
			id = id,
			content = it?.content ?: MessageContent(),
			timestamp = it?.timestamp.orNow()
		)
	}
	
	private suspend fun assistantMessage(id: UUID) = message<AgentMessage.Assistant>(id).let {
		RuntimeContext.Message.Assistant(
			id = id,
			reasoning = it?.reasoning,
			content = it?.content,
			modelId = it?.model ?: UUID.randomUUID(),
			timestamp = it?.timestamp.orNow(),
			usage = it?.usage
		)
	}
	
	private suspend fun pendingToolCall(id: UUID) = message<AgentMessage.Tool.Call>(id).let {
		RuntimeContext.CurrentRound.PendingToolCall(
			id = id,
			timestamp = it?.timestamp.orNow(),
			callId = it?.callId.orEmpty(),
			callName = it?.callName.orEmpty(),
			arguments = it?.arguments.orEmpty(),
			reason = it?.reason.orEmpty(),
			validatedToolName = it?.validatedToolName.orEmpty(),
			validatedArgs = it?.validatedArgs ?: JsonNull,
			resolvedRequest = it?.resolvedRequest ?: JsonNull,
			presentation = it?.presentation.orEmpty()
		)
	}
	
	private suspend fun toolCall(id: UUID) = message<AgentMessage.Tool.Call>(id).let {
		RuntimeContext.Message.Tool.Call(
			id = id,
			callName = it?.callName.orEmpty(),
			arguments = it?.arguments.orEmpty(),
			reason = it?.reason,
			timestamp = it?.timestamp.orNow(),
			validatedToolName = it?.validatedToolName.orEmpty(),
			validatedArgs = it?.validatedArgs,
			resolvedRequest = it?.resolvedRequest,
			presentation = it?.presentation
		)
	}
	
	private suspend fun toolResult(id: UUID) = message<AgentMessage.Tool.Result>(id).let {
		RuntimeContext.Message.Tool.Result(
			id = id,
			content = it?.content.orEmpty(),
			data = it?.data,
			presentation = it?.presentation.orEmpty(),
			timestamp = it?.timestamp.orNow(),
			status = it?.status ?: ToolResultStatus.FAILURE
		)
	}
	
	private suspend fun summarizedMessage(id: UUID) = message<AgentMessage.Compact>(id).let {
		RuntimeContext.SummarizedMessage(
			id = id,
			timestamp = it?.timestamp.orNow(),
			content = it?.content.orEmpty(),
			usage = it?.usage
		)
	}
	
	private suspend inline fun <reified T : AgentMessage> message(id: UUID): T? =
		(messages[id] ?: run { loadAll(setOf(id)); messages[id] }) as? T
	
	private suspend fun loadAll(ids: Set<UUID>) = loadMessages(ids).forEach {
		messages[it.id] = it
	}
	
	@AutoService(SettingDef::class)
	class KeepCompactedRounds : IntSetting(
		5,
		zh("初始化Agent时要加载到内存的已压缩轮次数量")
	)
}
