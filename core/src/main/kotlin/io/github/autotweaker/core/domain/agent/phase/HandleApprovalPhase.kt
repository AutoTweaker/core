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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator
import io.github.autotweaker.core.domain.agent.tool.Tools
import org.slf4j.LoggerFactory

object HandleApprovalPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun execute(
		env: AgentEnvironment,
		approvals: List<ToolApprove>,
		executeTool: suspend (ToolCallValidator.ValidationResult.Success, AgentContext.CurrentRound.PendingToolCall) -> AgentContext.Message.Tool,
	): PhaseResult {
		logger.debug(
			"Tool approval phase started  agentId={}  approvals={}",
			env.agentId, approvals.size
		)
		env.updateStatus(AgentStatus.PROCESSING)
		
		//读取上下文
		val needs = env.agentState.pendingApproval ?: return PhaseResult.Done
		val round = env.context.value.currentRound ?: return PhaseResult.Done
		val pendingCalls = round.pendingToolCalls ?: return PhaseResult.Done
		//读取assistantMessage
		val assistantMsg = requireNotNull(round.assistantMessage)
		//读取toolCall
		val callById = pendingCalls.associateBy { it.callId }
		val approvalByCallId = approvals.associateBy { it.callId }
		
		//存储未处理和已处理toolCall
		val remaining = mutableListOf<Tools.ToolCallResolveResult.NeedsApproval>()
		val processed = mutableListOf<AgentContext.Message.Tool>()
		
		//遍历pendingApproval
		for (n in needs) {
			val call = callById.getValue(n.callId)
			val a = approvalByCallId[n.callId]
			//当前pendingApproval未批准
			if (a == null) {
				logger.debug(
					"Tool approval deferred  agentId={}  tool={}  callId={}",
					env.agentId,
					call.name,
					call.callId
				)
				remaining.add(n)
				continue
			}
			//当前pendingApproval被批准或拒绝
			processed.add(
				if (a.approved) {
					logger.debug("Tool approved  agentId={}  tool={}  callId={}", env.agentId, call.name, call.callId)
					if (a.reason != null) env.agentState.approvalReasons.add(a.reason!!)
					executeTool(n.result, call)
				} else {
					logger.debug("Tool rejected  agentId={}  tool={}  callId={}", env.agentId, call.name, call.callId)
					ContextPhase.buildRejectedTool(call, a.reason, env)
				}
			)
			//如果已暂停，放弃剩余ToolCall
			if (env.status == AgentStatus.PAUSED) {
				needs.drop(needs.indexOf(n) + 1).forEach { remaining.add(it) }
				break
			}
		}
		
		//更新已处理/未处理列表
		env.agentState.pendingApproval = remaining.ifEmpty { null }
		env.agentState.processedTools = (env.agentState.processedTools.orEmpty() + processed)
		//清理pendingToolCalls
		ContextPhase.keepPendingCalls(remaining.map { it.callId }.toSet(), env::updateContext)
		
		return if (remaining.isEmpty()) {
			//都处理完，创建Turn并继续
			ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		} else {
			//还有未处理的，恢复状态
			env.updateStatus(AgentStatus.WAITING)
			PhaseResult.Done
		}
	}
}
