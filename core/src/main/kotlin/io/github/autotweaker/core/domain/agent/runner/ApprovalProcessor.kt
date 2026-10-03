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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.discard
import io.github.autotweaker.api.format
import io.github.autotweaker.api.get
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolCallStatus
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.MessageBuilder
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ApprovalProcessor(
	private val ctx: ContextManager,
	private val tool: ToolCallingStage,
	private val msg: MessageBuilder,
	private val status: MutableStateFlow<AgentStatus>,
	private val shouldBreak: StateFlow<Boolean>,
) {
	val approvalChannel = Channel<ToolApprove>(Channel.BUFFERED)
	
	fun shutdown() = approvalChannel.close().discard()
	
	suspend fun process(
		model: AgentModel,
	): List<String> = coroutineScope {
		val approvals = ConcurrentHashMap<UUID, ToolApprove>()
		val router = launch { route(approvals) }
		
		for (call in ctx.toolCalls!!.second) {
			if (call.status.value == ToolCallStatus.FINISHED) continue
			if (shouldBreak.value) break
			
			status.value = AgentStatus.WAITING
			call.awaitWaiting()
			status.value = AgentStatus.PROCESSING
			
			if (shouldBreak.value) break
			
			val approval = approvals[call.call.id]!!
			if (approval.approved) tool.execute(call, model, ctx.get())
			else call.reject(approval.reason)
		}
		
		router.cancel()
		approvals.values.mapNotNull { if (it.approved) it.reason else null }
	}
	
	private suspend fun route(
		approvals: MutableMap<UUID, ToolApprove>,
	) {
		for (approval in approvalChannel) {
			val target = ctx.toolCalls?.second?.find { it.call.callId == approval.callId } ?: continue
			if (target.status.value != ToolCallStatus.PENDING) continue
			approvals[target.call.id] = approval
			target.waiting()
		}
	}
	
	private suspend fun AgentToolCallImpl.awaitWaiting() {
		combine(status, shouldBreak) { status, broken ->
			status == ToolCallStatus.WAITING || broken
		}.first { it }
	}
	
	private suspend fun AgentToolCallImpl.reject(
		reason: String?,
	) = finish(
		msg.toolResult(
			callId = call.callId,
			content = if (reason != null) ToolSettings.RejectedWithFeedback().format(reason)
			else ToolSettings.Rejected().get(),
			data = null,
			presentation = resolved!!.rejected(reason),
			status = ToolResultStatus.REJECTED
		)
	)
}
