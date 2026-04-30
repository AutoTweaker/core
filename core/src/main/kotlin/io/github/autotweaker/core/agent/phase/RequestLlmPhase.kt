package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.AgentStreamProcessor
import io.github.autotweaker.core.agent.StreamProcessResult
import io.github.autotweaker.core.agent.llm.AgentChatRequest

//调用llm
internal suspend fun requestLlmPhase(
	env: AgentEnvironment,
	streamProcessor: AgentStreamProcessor,
): PhaseResult {
	env.updateStatus(AgentStatus.PROCESSING)
	
	val request = AgentChatRequest(
		model = env.currentModel,
		fallbackModels = env.currentFallbackModels,
		thinking = env.currentThinking,
		tools = env.tools.assembleTools(),
		context = env.context,
	)
	
	return when (streamProcessor.process(request, env.context)) {
		is StreamProcessResult.Completed -> {
			archiveCurrentRound(env)
			env.updateStatus(AgentStatus.FREE)
			PhaseResult.Done
		}
		
		is StreamProcessResult.ToolCallsRequired -> {
			PhaseResult.Continue
		}
		
		is StreamProcessResult.Cancelled -> {
			archiveCurrentRound(env)
			env.updateStatus(AgentStatus.FREE)
			PhaseResult.Done
		}
		
		is StreamProcessResult.Failed -> {
			env.updateStatus(AgentStatus.ERROR)
			PhaseResult.Error
		}
	}
}
