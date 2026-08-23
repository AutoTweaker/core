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

package io.github.autotweaker.core.domain.agent.compact

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeContext.SummarizedMessage
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.chat.inject
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.agent.compact.SummaryService.summarizeMessage
import io.github.autotweaker.core.domain.agent.runner.AgentContextManager
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.*
import kotlin.time.Clock

class CompactService(
	private val agentId: UUID,
	private val onOutput: (RuntimeOutput) -> Unit,
) : Loggable, Traceable {
	private val thinking = CompactSettings.Thinking().get()
	private val compactPrompt = CompactSettings.Prompt().get()
	private val maxMessageChars = CompactSettings.MaxMessageChars().get()
	private val messageSummarizePrompt = CompactSettings.MessageSummarizePrompt().get()
	
	suspend fun execute(
		model: AgentModel,
		ctx: AgentContextManager,
	) {
		val context = ctx.get()
		val rounds = context.historyRounds ?: return
		
		log.info(
			"Started compact  agentId={}  rounds={}  summarizeModel={}",
			agentId, rounds.size, model.summarize.id
		)
		
		val maxRetries = CompactSettings.MaxCompactRetries().get()
		
		val processedMessages = preprocessMessages(
			rounds, model, messageSummarizePrompt,
		).inject(
			context.injections, context.compactedRounds?.summarizedMessage?.content
		) + ChatMessage.User(
			compactPrompt.textPart(),
			Clock.System.now()
		)
		
		var attempt = 0
		var finalResult: SummarizedMessage?
		do {
			finalResult = runCompactRequest(
				model, processedMessages
			)
			attempt++
		} while (finalResult == null && attempt < maxRetries)
		
		if (finalResult == null) {
			log.warn("Failed compact  agentId={}  attempts={}", agentId, attempt)
			
			onOutput(
				RuntimeOutput.Error(
					AgentError(
						"Compact failed after $attempt attempts",
						AgentError.Type.COMPACT
					)
				)
			)
			return
		}
		
		log.info(
			"Completed compact  agentId={}  roundCount={}  attempts={}  summaryLength={}",
			agentId, rounds.size, attempt, finalResult.content.length
		)
		
		ctx.applyCompact(finalResult, rounds)
	}
	
	private suspend fun runCompactRequest(
		model: AgentModel,
		messages: List<ChatMessage>,
	): SummarizedMessage? {
		var streamContent = ""
		var lastResult: Pair<UUID, ChatMessage.Assistant>? = null
		var lastUsage: Usage? = null
		trace.catching {
			val results = ResilientChat.execute(
				model = model.summarize,
				fallbackModels = model.fallback,
				messages = messages,
				stream = true,
				reasoning = ReasoningEffort(thinking)
			)
			results.collect { resilientResult ->
				currentCoroutineContext().ensureActive()
				when (val result = resilientResult.result) {
					is ChatResult.Chunk -> if (!result.content.isNullOrEmpty()) {
						streamContent += result.content
						onOutput(
							RuntimeOutput.Compact(
								CompactOutput(
									CompactOutput.Status.OUTPUTTING,
									streamContent,
									null
								)
							)
						)
					}
					
					is ChatResult.Assembled -> {
						result.usage?.let { lastUsage = it }
						lastResult = resilientResult.model to result.message
					}
					
					else -> {}
				}
			}
		}.rethrowCancellation {
			log.debug("Cancelled compact  agentId={}", agentId)
		}.getOrElse { e ->
			log.warn("Failed compact request send  agentId={}  reason={}", agentId, e.message)
			onOutput(RuntimeOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, streamContent, null)))
			return null
		}
		
		val extracted = lastResult?.second?.content?.extractSummary()
		val minSummaryLength = CompactSettings.MinSummaryLength().get()
		val valid = extracted?.let { it.length >= minSummaryLength } ?: false
		
		if (valid) {
			onOutput(
				RuntimeOutput.Compact(
					CompactOutput(
						CompactOutput.Status.FINISHED,
						extracted,
						lastUsage
					)
				)
			
			)
			return SummarizedMessage(
				id = UUID(),
				timestamp = lastResult.second.timestamp,
				content = extracted,
				modelId = lastResult.first,
				usage = lastUsage
			)
		} else {
			log.warn("Found compact summary too short  agentId={}  length={}", agentId, extracted?.length ?: 0)
			if (lastUsage != null && lastResult != null)
				onOutput(
					RuntimeOutput.UsageConsumed(
						UsageEntry(
							modelId = lastResult.first,
							timestamp = lastResult.second.timestamp,
							usage = lastUsage
						)
					)
				)
			onOutput(
				RuntimeOutput.Compact(
					CompactOutput(
						CompactOutput.Status.FAILED, streamContent, lastUsage
					)
				)
			)
			return null
		}
	}
	
	private suspend fun preprocessMessages(
		rounds: List<RuntimeContext.CompletedRound>,
		model: AgentModel,
		messageSummarizePrompt: String,
	): List<ChatMessage> = buildList {
		rounds.forEach { round ->
			add(
				convertUserMessage(
					round.userMessage,
					messageSummarizePrompt,
					model,
				)
			)
			
			round.turns?.forEach { turn ->
				val toolCalls = turn.tools.map { tool ->
					ChatMessage.Assistant.ToolCall(
						id = tool.callId, name = tool.call.callName, arguments = tool.call.arguments
					)
				}
				add(
					convertAssistantMessage(
						turn.assistantMessage, toolCalls, messageSummarizePrompt, model
					)
				)
				turn.tools.forEach {
					add(
						convertToolMessage(
							it, messageSummarizePrompt, model
						)
					)
				}
			}
			round.finalAssistantMessage?.let {
				add(
					convertAssistantMessage(
						it, null, messageSummarizePrompt, model
					)
				)
			}
		}
	}
	
	private suspend fun convertUserMessage(
		msg: RuntimeContext.Message.User,
		prompt: String,
		model: AgentModel,
	): ChatMessage.User {
		val content = msg.content.inject().merge()
		val final = maybeSummarize(content, prompt, model)
		return ChatMessage.User(final.textPart(), msg.timestamp)
	}
	
	private suspend fun convertAssistantMessage(
		msg: RuntimeContext.Message.Assistant,
		toolCalls: List<ChatMessage.Assistant.ToolCall>?,
		prompt: String,
		model: AgentModel,
	) = ChatMessage.Assistant(
		content = maybeSummarize(msg.content.orEmpty(), prompt, model),
		timestamp = msg.timestamp,
		reasoningContent = msg.reasoning, toolCalls = toolCalls,
	)
	
	private suspend fun convertToolMessage(
		msg: RuntimeContext.Message.Tool,
		prompt: String,
		model: AgentModel,
	) = ChatMessage.ToolResult(
		content = maybeSummarize(msg.result.content, prompt, model),
		timestamp = msg.result.timestamp,
		toolCallId = msg.callId
	)
	
	
	private suspend fun maybeSummarize(
		content: String,
		prompt: String,
		model: AgentModel,
	): String = if (content.length > maxMessageChars)
		summarizeMessage(content, prompt, model, thinking).also {
			it.second?.let { usage ->
				onOutput(RuntimeOutput.UsageConsumed(usage))
			}
		}.first
	else content
	
	private fun String.extractSummary(): String =
		substringAfter("<summary>").substringBefore("</summary>").trim()
}
