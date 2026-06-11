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
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.session.ToolCallRequest
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.tool.Tools
import org.slf4j.LoggerFactory

object ValidateToolCallsPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun execute(env: AgentEnvironment): PhaseResult {
		logger.debug(
			"Tool calls validation started  agentId={}  pendingCallCount={}",
			env.agentId,
			env.context.value.currentRound?.pendingToolCalls?.size
		)
		env.updateStatus(AgentStatus.PROCESSING)
		//读取上下文
		val round = env.context.value.currentRound ?: return PhaseResult.Done
		val pendingCalls = round.pendingToolCalls ?: return PhaseResult.Done
		val assistantMsg = requireNotNull(round.assistantMessage)
		//解析工具调用
		val results = env.tools.resolveToolCalls(pendingCalls, env.agentId)
		val callById = pendingCalls.associateBy { it.callId }
		
		//分别提取解析失败、成功和激活的
		val failures = results.filterIsInstance<Tools.ToolCallResolveResult.ParseFailure>()
		val needsApproval = results.filterIsInstance<Tools.ToolCallResolveResult.NeedsApproval>()
		//填充validatedArgs和reason
		val validatedMap = if (needsApproval.isNotEmpty()) {
			needsApproval.associate { n ->
				n.callId to Pair(
					env.tools.serializeValidatedArgs(n.result.toolName, n.result.args),
					n.result.reason,
				)
			}
		} else {
			emptyMap()
		}
		if (needsApproval.isNotEmpty()) {
			env.updateContext { ctx ->
				val current = ctx.currentRound ?: return@updateContext ctx
				val pending = current.pendingToolCalls ?: return@updateContext ctx
				ctx.copy(
					currentRound = current.copy(
						pendingToolCalls = pending.map { call ->
							validatedMap[call.callId]?.let { (args, reason) ->
								call.copy(validatedArgs = args, reason = reason)
							} ?: call
						}
					)
				)
			}
		}
		val activations = results.filterIsInstance<Tools.ToolCallResolveResult.Activation>()
		logger.debug(
			"Tool calls resolved  agentId={}  total={}  failures={}  needsApproval={}  activations={}",
			env.agentId,
			pendingCalls.size,
			failures.size,
			needsApproval.size,
			activations.size
		)
		//构建解析失败的工具消息
		val errorTools = failures.map { f ->
			ContextPhase.buildErrorTool(callById.getValue(f.callId), f.errorMessage)
		}
		//构建激活成功的工具消息
		val activationTools = activations.map { a ->
			ContextPhase.buildToolResult(callById.getValue(a.callId), a.message, ToolResultStatus.SUCCESS)
		}
		env.agentState.processedTools = errorTools + activationTools
		
		//清理pendingToolCalls
		if (errorTools.isNotEmpty() || activationTools.isNotEmpty()) {
			ContextPhase.keepPendingCalls(needsApproval.map { it.callId }.toSet(), env::updateContext)
		}
		
		return if (needsApproval.isNotEmpty()) {
			//存储需要批准的工具请求
			env.agentState.pendingApproval = needsApproval
			logger.debug(
				"Tool calls queued for approval  agentId={}  count={}", env.agentId, needsApproval.size
			)
			env.emitOutput(AgentOutput.ToolRequest(needsApproval.map { n ->
				val call = callById.getValue(n.callId)
				ToolCallRequest(
					toolName = call.name,
					arguments = call.arguments,
					validatedArgs = validatedMap[n.callId]?.first,
					reason = n.result.reason,
					callId = n.callId
				)
			}))
			env.updateStatus(AgentStatus.WAITING)
			PhaseResult.Done
		} else {
			logger.debug(
				"All tool calls resolved  continued  agentId={}  processedToolCount={}", env.agentId, errorTools.size
			)
			//没有待处理的，直接继续
			ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		}
	}
}
