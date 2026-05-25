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

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentStreamProcessor
import io.github.autotweaker.core.agent.AgentStreamProcessor.StreamProcessResult
import io.github.autotweaker.core.agent.llm.AgentChatRequest
import org.slf4j.LoggerFactory

internal object RequestLlmPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	internal suspend fun execute(
		env: AgentEnvironment,
	): PhaseResult {
		logger.debug(
			"LLM request phase started  agentId={}  model={}  hasFallback={}  thinking={}",
			env.agentId,
			env.currentModel.modelInfo.modelId,
			env.currentFallbackModels != null,
			env.currentThinking
		)
		env.updateStatus(AgentStatus.PROCESSING)
		
		val request = AgentChatRequest(
			model = env.currentModel,
			fallbackModels = env.currentFallbackModels,
			thinking = env.currentThinking,
			tools = env.tools.assembleTools(),
			context = env.context.value,
		)
		
		return when (AgentStreamProcessor.processRequest(
			request, env.agentId, env.service, env::updateContext, env::emitOutput
		)) {
			is StreamProcessResult.Completed -> {
				ContextPhase.archiveCurrentRound(env, env::updateContext)
				env.updateStatus(AgentStatus.FREE)
				PhaseResult.Done
			}
			
			is StreamProcessResult.ToolCallsRequired -> {
				PhaseResult.Continue
			}
			
			is StreamProcessResult.Cancelled -> {
				ContextPhase.archiveCurrentRound(env, env::updateContext)
				env.updateStatus(AgentStatus.FREE)
				PhaseResult.Done
			}
			
			is StreamProcessResult.Failed -> {
				env.updateStatus(AgentStatus.ERROR)
				PhaseResult.Error
			}
		}
	}
}
