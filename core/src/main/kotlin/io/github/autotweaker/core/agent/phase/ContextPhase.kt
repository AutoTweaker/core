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

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import kotlin.time.Clock

//归档当前round
internal suspend fun archiveCurrentRound(
	env: AgentEnvironment,
	updateContext: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
) {
	//读取当前round
	val round = env.context.currentRound ?: return
	
	//当前round啥也没有（只有用户消息），直接丢弃
	if (round.assistantMessage == null && round.turns.isNullOrEmpty() && round.pendingToolCalls.isNullOrEmpty()) {
		updateContext { it.copy(currentRound = null) }
		return
	}
	
	val assistantMsg = round.assistantMessage
	//处理pendingToolCalls
	val canceledTools = round.pendingToolCalls?.map { call ->
		buildCancelledTool(call, env.toolCancelledMessage)
	}
	env.agentState.pendingApproval = null
	
	//将已处理和已取消的工具打包为Turn
	val archivedTurn = if (assistantMsg != null) {
		val archivedTools = (env.agentState.processedTools.orEmpty() + canceledTools.orEmpty())
		archivedTools.takeIf { it.isNotEmpty() }?.let {
			AgentContext.Turn(assistantMsg, it)
		}
	} else null
	env.agentState.processedTools = null
	
	//收集所有Turns
	val allTurns = buildList {
		round.turns?.let { addAll(it) }
		archivedTurn?.let { add(it) }
	}.ifEmpty { null }
	
	//更新AgentContext
	updateContext { ctx ->
		val completed = AgentContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = if (archivedTurn != null) null else assistantMsg,
		)
		ctx.copy(
			currentRound = null,
			historyRounds = ctx.historyRounds.orEmpty() + completed,
		)
	}
	env.emitOutput(AgentOutput.ContextUpdate(env.context, AgentOutput.ContextUpdate.UpdateReason.ARCHIVED))
}

//将处理完的工具连同assistantMessage转为一个Turn并继续
internal suspend fun writeToolTurn(
	env: AgentEnvironment,
	assistantMsg: AgentContext.Message.Assistant,
	updateContext: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
): PhaseResult {
	//读取并清空已处理工具的列表
	val tools = env.agentState.processedTools.orEmpty()
	env.agentState.processedTools = null
	if (tools.isEmpty()) return PhaseResult.Done
	env.context.currentRound ?: return PhaseResult.Done
	//更新上下文
	updateContext { ctx ->
		val cr = ctx.currentRound ?: return@updateContext ctx
		ctx.copy(
			currentRound = cr.copy(
				assistantMessage = null,
				turns = (cr.turns ?: emptyList()) + AgentContext.Turn(assistantMsg, tools),
			)
		)
	}
	//发送批准原因
	val reasons = env.agentState.approvalReasons.toList()
	env.agentState.approvalReasons.clear()
	if (reasons.isNotEmpty()) {
		val userMsg = AgentContext.Message.User(
			content = reasons.joinToString("\n---\n"),
			timestamp = Clock.System.now()
		)
		//归档当前round，开启新round
		archiveCurrentRound(env, updateContext)
		updateContext { ctx ->
			ctx.copy(currentRound = AgentContext.CurrentRound(userMessage = userMsg, turns = null))
		}
	}
	env.emitOutput(AgentOutput.ContextUpdate(env.context, AgentOutput.ContextUpdate.UpdateReason.TOOL))
	return PhaseResult.Continue
}

//清理已处理的pendingToolCalls
internal suspend fun keepPendingCalls(
	callIds: Set<String>,
	updateContext: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
) {
	updateContext { ctx ->
		val cr = ctx.currentRound ?: return@updateContext ctx
		val pending = cr.pendingToolCalls ?: return@updateContext ctx
		ctx.copy(
			currentRound = cr.copy(
				pendingToolCalls = pending.filter { it.callId in callIds }.ifEmpty { null }
			)
		)
	}
}

//构建工具消息
internal fun buildToolResult(
	call: AgentContext.CurrentRound.PendingToolCall,
	content: String,
	status: AgentContext.Message.Tool.Result.Status,
): AgentContext.Message.Tool = AgentContext.Message.Tool(
	name = call.name,
	call = AgentContext.Message.Tool.Call(
		assistantMessageId = call.assistantMessageId,
		arguments = call.arguments,
		reason = call.reason,
		timestamp = call.timestamp,
		model = call.model,
	),
	callId = call.callId,
	result = AgentContext.Message.Tool.Result(
		content = content,
		timestamp = Clock.System.now(),
		status = status,
	),
)

//构建错误、拒绝、取消的工具消息
internal fun buildErrorTool(
	call: AgentContext.CurrentRound.PendingToolCall,
	errorMsg: String,
): AgentContext.Message.Tool = buildToolResult(call, errorMsg, AgentContext.Message.Tool.Result.Status.FAILURE)

internal fun buildRejectedTool(
	call: AgentContext.CurrentRound.PendingToolCall,
	feedbackReason: String?,
	env: AgentEnvironment,
): AgentContext.Message.Tool = buildToolResult(
	call,
	if (feedbackReason != null) env.toolRejectedWithFeedbackMessage.format(feedbackReason) else env.toolRejectedMessage,
	AgentContext.Message.Tool.Result.Status.CANCELLED,
)

private fun buildCancelledTool(
	call: AgentContext.CurrentRound.PendingToolCall,
	cancelledMessage: String,
): AgentContext.Message.Tool =
	buildToolResult(call, cancelledMessage, AgentContext.Message.Tool.Result.Status.CANCELLED)
