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

import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.runner.ToolMessageFactory.buildPending
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.tool.port.TruncationService
import java.nio.file.Path
import io.github.autotweaker.api.types.llm.ChatMessage.Assistant.ToolCall as RawCall

class ThinkingStage(
	private val llmService: LlmService,
	private val tools: Tools,
	private val workspace: () -> Path,
	private val truncation: TruncationService,
	private val onOutput: (RuntimeOutput) -> Unit,
) {
	suspend fun execute(
		model: AgentModel,
		assembledTools: List<ChatRequest.Tool>?,
		context: RuntimeContext,
	): Result? {
		val callResult = llmService.execute(model, assembledTools, context) ?: return null
		val rawCalls = callResult.toolCalls
		if (rawCalls.isNullOrEmpty()) return Result(
			assistantMessage = callResult.assistantMessage,
			activations = null,
			parseFailures = null,
			resolveFailures = null,
			needsApproval = null
		)
		
		val provider = ToolProvider.buildToolProvider(
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
			activations = calls.ofType(),
			parseFailures = calls.ofType(),
			resolveFailures = calls.ofType(),
			needsApproval = calls.mapNotNull { (call, resolved) ->
				if (resolved !is ResolveResult.NeedsApproval) return@mapNotNull null
				buildPending(
					callResult.assistantMessage.timestamp,
					call, resolved
				) to resolved.resolveResult
			}.orNull()
		)
	}
	
	
	private inline fun <reified T : ResolveResult> PairList<RawCall, ResolveResult>.ofType(): PairList<RawCall, T>? =
		mapNotNull { (call, resolved) ->
			(resolved as? T)?.let { call to it }
		}.orNull()
	
	data class Result(
		val assistantMessage: RuntimeContext.Message.Assistant,
		val activations: PairList<RawCall, ResolveResult.Activation>?,
		val parseFailures: PairList<RawCall, ResolveResult.ParseFailure>?,
		val resolveFailures: PairList<RawCall, ResolveResult.ResolveFailure>?,
		val needsApproval: PairList<RuntimeContext.CurrentRound.PendingToolCall, Tool.ResolveResult.Ready>?,
	)
}
