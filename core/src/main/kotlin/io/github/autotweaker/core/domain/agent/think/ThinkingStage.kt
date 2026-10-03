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

package io.github.autotweaker.core.domain.agent.think

import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path
import io.github.autotweaker.api.types.llm.ChatMessage.Assistant.ToolCall as RawCall

class ThinkingStage(
	private val llmService: LlmService,
	private val tools: Tools,
	private val provider: ToolProvider,
	private val workspace: () -> Path,
	private val truncation: TruncationService,
	private val status: MutableStateFlow<AgentStatus>,
	private val onOutput: (RuntimeOutput) -> Unit,
) {
	suspend fun execute(
		model: AgentModel,
		assembledTools: List<ChatRequest.Tool>?,
		context: RuntimeContext,
	): Result? {
		val callResult = llmService.execute(model, assembledTools, context) ?: return null
		status.value = AgentStatus.PROCESSING
		
		val rawCalls = callResult.toolCalls
		if (rawCalls.isNullOrEmpty()) return Result(
			assistantMessage = callResult.assistantMessage,
			toolCalls = null
		)
		
		val provider = provider.build(
			workspace = workspace,
			onOutput = onOutput,
			model = model,
			context = context,
			truncation = truncation,
		)
		val calls = buildList {
			rawCalls.forEach { rawCall ->
				val result = tools.resolveToolCall(rawCall, provider)
				add(rawCall to result)
			}
		}
		
		return Result(
			assistantMessage = callResult.assistantMessage,
			toolCalls = calls
		)
	}
	
	data class Result(
		val assistantMessage: AgentMessage.Assistant,
		val toolCalls: PairList<RawCall, ResolveResult>?,
	)
}
