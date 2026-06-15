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

import io.github.autotweaker.core.domain.agent.AgentContextManager
import io.github.autotweaker.core.domain.agent.think.ThinkingStage

class RoundContext(
	private val ctx: AgentContextManager,
	private val factory: ToolResultFactory,
) {
	suspend fun applyDone(result: ThinkingStage.Result.Done) {
		ctx.applyThinking(
			assistant = result.assistantMessage,
			pendingCalls = emptyList(),
			immediateResults = factory.buildImmediateResults(
				result.assistantMessage.id, result.assistantMessage.timestamp,
				result.activations, result.parseFailures,
			),
		)
	}
	
	suspend fun applyHasPending(result: ThinkingStage.Result.HasPending) {
		ctx.applyThinking(
			assistant = result.assistantMessage,
			pendingCalls = result.needsApproval.map { it.pendingCall },
			immediateResults = factory.buildImmediateResults(
				result.assistantMessage.id, result.assistantMessage.timestamp,
				result.activations, result.parseFailures,
			),
		)
	}
}
