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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.tool.Tools

//处理ToolCall请求
internal suspend fun validateToolCallsPhase(env: AgentEnvironment): PhaseResult {
	env.updateStatus(AgentStatus.PROCESSING)
	//读取上下文
	val round = env.context.currentRound ?: return PhaseResult.Done
	val pendingCalls = round.pendingToolCalls ?: return PhaseResult.Done
	val assistantMsg = requireNotNull(round.assistantMessage)
	//解析工具调用
	val results = env.tools.resolveToolCalls(pendingCalls)
	val callById = pendingCalls.associateBy { it.callId }
	
	//分别提取解析失败和成功的
	val failures = results.filterIsInstance<Tools.ToolCallResolveResult.ParseFailure>()
	val needsApproval = results.filterIsInstance<Tools.ToolCallResolveResult.NeedsApproval>()
	//构建解析失败的工具消息
	val errorTools = failures.map { f ->
		buildErrorTool(callById.getValue(f.callId), f.errorMessage)
	}
	env.agentState.processedTools = errorTools
	
	//清理pendingToolCalls
	if (errorTools.isNotEmpty()) {
		keepPendingCalls(needsApproval.map { it.callId }.toSet(), env::updateContext)
	}
	
	return if (needsApproval.isNotEmpty()) {
		//存储需要批准的工具请求
		env.agentState.pendingApproval = needsApproval
		val needsApprovalCalls = needsApproval.map { callById.getValue(it.callId) }
		env.emitOutput(AgentOutput.ToolCallRequest(needsApprovalCalls))
		env.updateStatus(AgentStatus.WAITING)
		PhaseResult.Done
	} else {
		//没有待处理的，直接继续
		writeToolTurn(env, assistantMsg, env::updateContext)
	}
}
