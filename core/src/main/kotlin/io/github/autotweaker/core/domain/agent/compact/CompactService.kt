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

import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentContext.SummarizedMessage
import io.github.autotweaker.core.domain.agent.AgentContextManager
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.chat.inject
import io.github.autotweaker.core.domain.agent.compact.SummaryService.findModelInfo
import io.github.autotweaker.core.domain.agent.compact.SummaryService.summarizeMessage
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.*
import kotlin.time.Clock
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.config.Settable
import io.github.autotweaker.api.config.setting
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.trace

class CompactService(
	private val agentId: UUID,
	private val onOutput: suspend (AgentOutput) -> Unit,
) : Loggable, Traceable, Settable {
	
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
		
		val compactPrompt = setting.get(CompactSettings.Prompt()).value
		val maxMessageChars = setting.get(CompactSettings.MaxMessageChars()).value
		val messageSummarizePrompt = setting.get(CompactSettings.MessageSummarizePrompt()).value
		val thinkingEnabled = setting.get(CompactSettings.Thinking()).value
		val maxRetries = setting.get(CompactSettings.MaxCompactRetries()).value
		
		val (preprocessedMessages, preprocessSnapshots) = preprocessMessages(
			rounds, model, maxMessageChars, messageSummarizePrompt, thinkingEnabled
		)
		
		val processedMessages = preprocessedMessages.inject(context.injections, context.summarizedMessage?.content)
		val systemAndMessages = processedMessages + ChatMessage.UserMessage(compactPrompt, Clock.System.now())
		
		val snapshots = preprocessSnapshots.toMutableList()
		var attempt = 0
		var finalResult: CompactRequestResult
		do {
			finalResult = runCompactRequest(
				model, systemAndMessages, thinkingEnabled
			)
			attempt++
			finalResult.snapshot?.let { snapshots.add(it) }
		} while (!finalResult.success && attempt < maxRetries)
		
		if (!finalResult.success) {
			log.warn(
				"Failed compact  agentId={}  attempts={}", agentId, attempt
			)
			snapshots.forEach {
				onOutput(AgentOutput.UsageConsumed(Clock.System.now(), it.usage, it.model))
			}
			onOutput(
				AgentOutput.Error(
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
		
		val compactMsg = SummarizedMessage(
			timestamp = Clock.System.now(),
			content = finalResult.content,
			snapshots = snapshots.associateBy { UUID.randomUUID() },
		)
		
		ctx.applyCompact(compactMsg, rounds)
	}
	
	private data class CompactRequestResult(
		val content: String,
		val snapshot: UsageSnapshot?,
		val success: Boolean,
	)
	
	private suspend fun runCompactRequest(
		model: AgentModel,
		messages: List<ChatMessage>,
		thinkingEnabled: Boolean,
	): CompactRequestResult {
		var rawContent = ""
		var lastSnapshot: UsageSnapshot? = null
		try {
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
							rawContent += msg.content
							onOutput(
								AgentOutput.Compact(
									CompactOutput(
										CompactOutput.Status.OUTPUTTING,
										rawContent,
										null
									)
								)
							)
						}
					}
					
					is ChatResult.Assembled -> {
						val assistantMsg = result.message as? ChatMessage.AssistantMessage ?: return@collect
						if (!assistantMsg.content.isNullOrEmpty()) rawContent = assistantMsg.content!!
						result.usage?.let {
							lastSnapshot =
								UsageSnapshot(it, model.findModelInfo(resilientResult.model))
						}
					}
				}
			}
		} catch (e: CancellationException) {
			trace.exception(e)
			log.debug("Cancelled compact  agentId={}", agentId)
			throw e
		} catch (e: Exception) {
			trace.exception(e)
			log.warn("Failed compact request send  agentId={}  reason={}", agentId, e.message)
			onOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, rawContent, null)))
			return CompactRequestResult(rawContent, lastSnapshot, success = false)
		}
		
		val extracted = rawContent.extractSummary()
		val minSummaryLength = setting.get(CompactSettings.MinSummaryLength()).value
		val valid = extracted.length >= minSummaryLength
		
		if (valid) {
			onOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FINISHED, rawContent, lastSnapshot?.usage)))
		} else {
			log.warn("Found compact summary too short  agentId={}  length={}", agentId, extracted.length)
			onOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, rawContent, lastSnapshot?.usage)))
		}
		
		return CompactRequestResult(extracted, lastSnapshot, success = valid)
	}
	
	private data class PreprocessResult(
		val messages: List<ChatMessage>,
		val snapshots: List<UsageSnapshot>,
	)
	
	private suspend fun preprocessMessages(
		rounds: List<AgentContext.CompletedRound>,
		model: AgentModel,
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		thinkingEnabled: Boolean,
	): PreprocessResult {
		val messages = mutableListOf<ChatMessage>()
		val snapshots = mutableListOf<UsageSnapshot>()
		
		rounds.forEach { round ->
			val (userMsg, userSnapshot) = convertUserMessage(
				round.userMessage,
				maxMessageChars,
				messageSummarizePrompt,
				model,
				thinkingEnabled
			)
			userSnapshot?.let { snapshots.add(it) }
			messages.add(userMsg)
			
			round.turns?.forEach { turn ->
				val toolCalls = turn.tools.map { tool ->
					ChatMessage.AssistantMessage.ToolCall(
						id = tool.callId, name = tool.name, arguments = tool.call.arguments
					)
				}
				val (assistantMsg, assistantSnapshot) = convertAssistantMessage(
					turn.assistantMessage, toolCalls, maxMessageChars, messageSummarizePrompt,
					model, thinkingEnabled
				)
				assistantSnapshot?.let { snapshots.add(it) }
				messages.add(assistantMsg)
				turn.tools.forEach {
					val (toolMsg, toolSnapshot) = convertToolMessage(
						it, maxMessageChars, messageSummarizePrompt, model, thinkingEnabled
					)
					toolSnapshot?.let { snapshot -> snapshots.add(snapshot) }
					messages.add(toolMsg)
				}
			}
			round.finalAssistantMessage?.let {
				val (assistantMsg, assistantSnapshot) = convertAssistantMessage(
					it, null, maxMessageChars, messageSummarizePrompt, model, thinkingEnabled
				)
				assistantSnapshot?.let { snapshot -> snapshots.add(snapshot) }
				messages.add(assistantMsg)
			}
		}
		
		return PreprocessResult(messages, snapshots)
	}
	
	private suspend fun maybeSummarize(
		content: String,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): Pair<String, UsageSnapshot?> =
		if (content.length > maxChars) summarizeMessage(content, prompt, model, thinking)
		else content to null
	
	private suspend fun convertUserMessage(
		msg: AgentContext.Message.User,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): Pair<ChatMessage.UserMessage, UsageSnapshot?> {
		val content = msg.content.inject(true)
		val (final, snapshot) = maybeSummarize(content, maxChars, prompt, model, thinking)
		return ChatMessage.UserMessage(final, msg.timestamp) to snapshot
	}
	
	private suspend fun convertAssistantMessage(
		msg: AgentContext.Message.Assistant,
		toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): Pair<ChatMessage.AssistantMessage, UsageSnapshot?> {
		val (final, snapshot) = maybeSummarize(msg.content.orEmpty(), maxChars, prompt, model, thinking)
		return ChatMessage.AssistantMessage(
			content = final, createdAt = msg.timestamp,
			reasoningContent = msg.reasoning, toolCalls = toolCalls, model = null,
		) to snapshot
	}
	
	private suspend fun convertToolMessage(
		msg: AgentContext.Message.Tool,
		maxChars: Int,
		prompt: String,
		model: AgentModel,
		thinking: Boolean,
	): Pair<ChatMessage.ToolMessage, UsageSnapshot?> {
		val (final, snapshot) = maybeSummarize(msg.result.content, maxChars, prompt, model, thinking)
		return ChatMessage.ToolMessage(
			content = final,
			createdAt = msg.result.timestamp,
			toolCallId = msg.callId
		) to snapshot
	}
	
	private fun String.extractSummary(): String =
		substringAfter("<summary>").substringBefore("</summary>").trim()
}