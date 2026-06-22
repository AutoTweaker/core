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

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.core.domain.agent.AgentContextManager
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.trace

class ApprovalProcessor(
	private val ctx: AgentContextManager,
	private val tool: ToolCallingStage,
	private val factory: ToolResultFactory,
	private val workspace: WorkspaceMeta,
	private val containerConfig: ContainerConfig,
	private val scope: CoroutineScope,
	private val shouldBreak: StateFlow<Boolean>,
) : Traceable {
	val approvalChannel = Channel<ToolApprove>(Channel.UNLIMITED)
	
	fun cancelToolJob() {
		tool.cancelToolJob()
	}
	
	suspend fun process(
		needsApproval: List<ThinkingStage.ResolvedToolCall>,
		assistantMessageId: UUID,
		model: AgentModel,
		statusFlow: MutableStateFlow<AgentStatus>,
	): List<String> {
		val reasons = mutableListOf<String>()
		val stashed = mutableMapOf<String, ToolApprove>()
		
		for (call in needsApproval) {
			if (shouldBreak.value) break
			
			statusFlow.value = AgentStatus.WAITING
			var approval = stashed.remove(call.pendingCall.callId)
			while (approval == null) {
				val deferred = scope.async { approvalChannel.receive() }
				val watcher = scope.launch {
					shouldBreak.first { it }
					deferred.cancel()
				}
				val next = try {
					deferred.await()
				} catch (e: CancellationException) {
					trace.exception(e)
					return reasons
				} finally {
					watcher.cancel()
				}
				
				if (next.callId == call.pendingCall.callId) approval = next
				else stashed[next.callId] = next
			}
			
			if (shouldBreak.value) break
			
			if (approval.approved) {
				approval.reason?.let { reasons.add(it) }
				statusFlow.value = AgentStatus.TOOL_CALLING
				val deferred = scope.async {
					tool.execute(call, workspace, containerConfig, model, ctx.get())
				}
				val toolResult = deferred.await()
				ctx.recordToolResult(factory.buildToolMessage(assistantMessageId, call.pendingCall, toolResult))
			} else {
				ctx.recordToolResult(factory.buildRejected(assistantMessageId, call.pendingCall, approval.reason))
			}
		}
		return reasons
	}
}