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
	
	return when (streamProcessor.process(request)) {
		is StreamProcessResult.Completed -> {
			archiveCurrentRound(env, env::updateContext)
			env.updateStatus(AgentStatus.FREE)
			PhaseResult.Done
		}
		
		is StreamProcessResult.ToolCallsRequired -> {
			PhaseResult.Continue
		}
		
		is StreamProcessResult.Cancelled -> {
			archiveCurrentRound(env, env::updateContext)
			env.updateStatus(AgentStatus.FREE)
			PhaseResult.Done
		}
		
		is StreamProcessResult.Failed -> {
			env.updateStatus(AgentStatus.ERROR)
			PhaseResult.Error
		}
	}
}
