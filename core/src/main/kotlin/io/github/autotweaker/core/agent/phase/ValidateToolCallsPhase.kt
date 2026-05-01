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
