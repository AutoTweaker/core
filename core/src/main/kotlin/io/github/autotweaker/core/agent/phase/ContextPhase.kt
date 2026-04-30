package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import kotlin.time.Clock

//归档当前round
internal fun archiveCurrentRound(env: AgentEnvironment) {
	//读取当前round
	val round = env.context.currentRound ?: return
	
	//当前round啥也没有（只有用户消息），直接丢弃
	if (round.assistantMessage == null && round.turns.isNullOrEmpty() && round.pendingToolCalls.isNullOrEmpty()) {
		env.context = env.context.copy(currentRound = null)
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
	
	//构建CompletedRound
	val completed = AgentContext.CompletedRound(
		userMessage = round.userMessage,
		turns = allTurns,
		finalAssistantMessage = assistantMsg,
	)
	
	//更新AgentContext
	env.context = env.context.copy(
		currentRound = null,
		historyRounds = env.context.historyRounds.orEmpty() + completed,
	)
}

//将处理完的工具连同assistantMessage转为一个Turn并继续
internal fun writeToolTurn(env: AgentEnvironment, assistantMsg: AgentContext.Message.Assistant): PhaseResult {
	//读取并清空已处理工具的列表
	val tools = env.agentState.processedTools.orEmpty()
	env.agentState.processedTools = null
	if (tools.isEmpty()) return PhaseResult.Done
	val round = env.context.currentRound ?: return PhaseResult.Done
	//更新上下文
	env.context = env.context.copy(
		currentRound = round.copy(
			assistantMessage = null,
			turns = (round.turns ?: emptyList()) + AgentContext.Turn(assistantMsg, tools),
		)
	)
	return PhaseResult.Continue
}

//清理已处理的pendingToolCalls
internal fun keepPendingCalls(env: AgentEnvironment, callIds: Set<String>) {
	val round = env.context.currentRound ?: return
	val pending = round.pendingToolCalls ?: return
	env.context = env.context.copy(
		currentRound = round.copy(
			pendingToolCalls = pending.filter { it.callId in callIds }.ifEmpty { null }
		)
	)
}

//构建工具消息
internal fun buildToolResult(
	call: AgentContext.CurrentRound.PendingToolCall,
	content: String,
	status: AgentContext.Message.Tool.Result.Status,
): AgentContext.Message.Tool = AgentContext.Message.Tool(
	name = call.name,
	call = AgentContext.Message.Tool.Call(
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
