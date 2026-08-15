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
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeContext.SummarizedMessage
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.chat.inject
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
		
		val compactPrompt = CompactSettings.Prompt().get()
		val maxMessageChars = CompactSettings.MaxMessageChars().get()
		val messageSummarizePrompt = CompactSettings.MessageSummarizePrompt().get()
		val thinkingEnabled = CompactSettings.Thinking().get()
		val maxRetries = CompactSettings.MaxCompactRetries().get()
		
		val processedMessages = preprocessMessages(
			rounds, model, maxMessageChars, messageSummarizePrompt, thinkingEnabled
		).inject(
			context.injections, context.compactedRounds?.summarizedMessage?.content
		) + ChatMessage.UserMessage(compactPrompt, Clock.System.now())
		
		var attempt = 0
		var finalResult: Pair<String, Usage?>?
		do {
			finalResult = runCompactRequest(
				model, processedMessages, thinkingEnabled
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
			agentId, rounds.size, attempt, finalResult.first.length
		)
		
		val compactMsg = SummarizedMessage(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			content = finalResult.first,
			usage = finalResult.second,
		)
		
		ctx.applyCompact(compactMsg, rounds)
	}
	
	private suspend fun runCompactRequest(
		model: AgentModel,
		messages: List<ChatMessage>,
		thinkingEnabled: Boolean,
	): Pair<String, Usage?>? {
		var streamContent = ""
		var lastResult: ChatMessage.AssistantMessage? = null
		var lastUsage: Usage? = null
		trace.catching {
			val results = ResilientChat.execute(
				model = model.summarize,
				fallbackModels = model.fallback,
				messages = messages,
				stream = true,
				thinking = thinkingEnabled,
			)
			results.collect { resilientResult ->
				currentCoroutineContext().ensureActive()
				when (val result = resilientResult.result) {
					is ChatResult.Chunk -> {
						val msg = result.message ?: return@collect
						if (!msg.content.isNullOrEmpty()) {
							streamContent += msg.content
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
					}
					
					is ChatResult.Assembled -> {
						result.usage?.let { lastUsage = it }
						val assistantMsg = result.message as? ChatMessage.AssistantMessage
						assistantMsg?.let { lastResult = it }
					}
				}
			}
		}.rethrowCancellation {
			log.debug("Cancelled compact  agentId={}", agentId)
		}.getOrElse { e ->
			log.warn("Failed compact request send  agentId={}  reason={}", agentId, e.message)
			onOutput(RuntimeOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, streamContent, null)))
			return null
		}
		
		val extracted = lastResult?.content?.extractSummary()
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
			return extracted to lastUsage
		} else {
			log.warn("Found compact summary too short  agentId={}  length={}", agentId, extracted?.length ?: 0)
			lastUsage?.let { onOutput(RuntimeOutput.UsageConsumed(Clock.System.now(), it)) }
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
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		thinkingEnabled: Boolean,
	): List<ChatMessage> = buildList {
		rounds.forEach { round ->
			add(
				convertUserMessage(
					round.userMessage,
					maxMessageChars,
					messageSummarizePrompt,
					model,
					thinkingEnabled
				)
			)
			
			round.turns?.forEach { turn ->
				val toolCalls = turn.tools.map { tool ->
					ChatMessage.AssistantMessage.ToolCall(
						id = tool.callId, name = tool.call.callName, arguments = tool.call.arguments
					)
				}
				add(
					convertAssistantMessage(
						turn.assistantMessage, toolCalls, maxMessageChars, messageSummarizePrompt,
						model, thinkingEnabled
					)
				)
				turn.tools.forEach {
					add(
						convertToolMessage(
							it, maxMessageChars, messageSummarizePrompt, model, thinkingEnabled
						)
					)
				}
			}
			round.finalAssistantMessage?.let {
				add(
					convertAssistantMessage(
						it, null, maxMessageChars, messageSummarizePrompt, model, thinkingEnabled
					)
				)
			}
		}
	}
	
	private suspend fun convertUserMessage(
		msg: RuntimeContext.Message.User,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): ChatMessage.UserMessage {
		val content = msg.content.inject(true)
		val final = maybeSummarize(content, maxChars, prompt, model, thinking)
		return ChatMessage.UserMessage(final, msg.timestamp)
	}
	
	private suspend fun convertAssistantMessage(
		msg: RuntimeContext.Message.Assistant,
		toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	) = ChatMessage.AssistantMessage(
		content = maybeSummarize(msg.content.orEmpty(), maxChars, prompt, model, thinking),
		createdAt = msg.timestamp,
		reasoningContent = msg.reasoning, toolCalls = toolCalls, model = null,
	)
	
	private suspend fun convertToolMessage(
		msg: RuntimeContext.Message.Tool,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	) = ChatMessage.ToolMessage(
		content = maybeSummarize(msg.result.content, maxChars, prompt, model, thinking),
		createdAt = msg.result.timestamp,
		toolCallId = msg.callId
	)
	
	
	private suspend fun maybeSummarize(
		content: String,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): String = if (content.length > maxChars)
		summarizeMessage(content, prompt, model, thinking).also {
			it.second?.let { usage ->
				onOutput(RuntimeOutput.UsageConsumed(Clock.System.now(), usage))
			}
		}.first
	else content
	
	private fun String.extractSummary(): String =
		substringAfter("<summary>").substringBefore("</summary>").trim()
}
