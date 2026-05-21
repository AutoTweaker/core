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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.ResilientChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock

internal object CompactPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	internal suspend fun execute(
		env: AgentEnvironment,
		rounds: List<AgentContext.CompletedRound>,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		service: SettingService,
	) {
		logger.debug(
			"Compact started  agentId={}  roundCount={}  summarizeModel={}",
			env.agentId,
			rounds.size,
			summarizeModel.modelInfo.modelId
		)
		
		val compactPrompt = service.get(CompactSettings.Prompt()).value
		val maxMessageChars = service.get(CompactSettings.MaxMessageChars()).value
		val messageSummarizePrompt = service.get(CompactSettings.MessageSummarizePrompt()).value
		
		val processed =
			preprocessMessages(rounds, summarizeModel, fallbackModels, maxMessageChars, messageSummarizePrompt, service)
		
		val systemAndMessages = processed + ChatMessage.UserMessage(compactPrompt, Clock.System.now())
		
		var attempt = 0
		var finalResult: CompactRequestResult
		while (true) {
			finalResult = runCompactRequest(env, summarizeModel, fallbackModels, systemAndMessages, service)
			attempt++
			
			if (finalResult.success || attempt >= service.get(CompactSettings.MaxCompactRetries()).value) break
		}
		
		val cleaned = finalResult.rawContent
		val minSummaryLength = service.get(CompactSettings.MinSummaryLength()).value
		if (cleaned.length < minSummaryLength) {
			logger.warn(
				"Failed to produce compact summary  result too short  agentId={}  attempts={}  length={}",
				env.agentId,
				attempt,
				cleaned.length
			)
			env.emitOutput(
				AgentOutput.Error(
					AgentError(
						"Compact produced summary shorter than $minSummaryLength chars after $attempt attempts",
						AgentError.Type.COMPACT
					)
				)
			)
			return
		}
		
		logger.debug(
			"Compact completed  agentId={}  roundCount={}  attempts={}  summaryLength={}",
			env.agentId,
			rounds.size,
			attempt,
			cleaned.length
		)
		
		val compactedIds = rounds.map { it.userMessage.id }.toSet()
		
		env.updateContext { ctx ->
			val dropped = ctx.historyRounds?.filter { it.userMessage.id in compactedIds }
			val remaining = ctx.historyRounds?.filter { it.userMessage.id !in compactedIds }?.ifEmpty { null }
			val compactMsg = AgentContext.SummarizedMessage(
				id = UUID.randomUUID(), timestamp = Clock.System.now(), content = cleaned
			)
			ctx.copy(
				historyRounds = remaining,
				compactedRounds = if (!dropped.isNullOrEmpty()) {
					(ctx.compactedRounds ?: emptyList()) + AgentContext.CompactedRound(
						rounds = dropped,
						summarizedMessage = compactMsg,
					)
				} else ctx.compactedRounds,
				summarizedMessage = compactMsg,
			)
		}
	}
	
	private data class CompactRequestResult(
		val rawContent: String,
		val usage: Usage?,
		val success: Boolean,
	)
	
	private suspend fun runCompactRequest(
		env: AgentEnvironment,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		messages: List<ChatMessage>,
		service: SettingService,
	): CompactRequestResult {
		var rawContent = ""
		var lastUsage: Usage? = null
		try {
			val results = ResilientChat.execute(
				model = summarizeModel,
				fallbackModels = fallbackModels,
				messages = messages,
				stream = true,
				thinking = false,
				service = service,
			)
			results.collect { resilientResult ->
				currentCoroutineContext().ensureActive()
				when (val result = resilientResult.result) {
					is ChatResult.Chunk -> {
						val msg = result.message ?: return@collect
						if (!msg.content.isNullOrEmpty()) {
							rawContent += msg.content
							env.emitOutput(
								AgentOutput.Compact(
									CompactOutput(
										CompactOutput.Status.OUTPUTTING, rawContent, null
									)
								)
							)
						}
						result.usage?.let { lastUsage = it }
					}
					
					is ChatResult.Assembled -> {
						val assistantMsg = result.message as? ChatMessage.AssistantMessage ?: return@collect
						if (!assistantMsg.content.isNullOrEmpty()) {
							rawContent = assistantMsg.content!!
						}
						result.usage?.let { lastUsage = it }
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			logger.warn("Failed to send compact request  agentId={}", env.agentId)
			env.emitOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, rawContent, null)))
			return CompactRequestResult(rawContent, lastUsage, success = false)
		}
		
		val extracted = extractSummary(rawContent)
		val valid = extracted.length >= service.get(CompactSettings.MinSummaryLength()).value
		
		if (valid) {
			env.emitOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FINISHED, rawContent, lastUsage)))
		} else {
			logger.debug(
				"Compact summary found too short  agentId={}  length={}", env.agentId, extracted.length
			)
			env.emitOutput(AgentOutput.Compact(CompactOutput(CompactOutput.Status.FAILED, rawContent, lastUsage)))
		}
		
		return CompactRequestResult(extracted, lastUsage, success = valid)
	}
	
	private suspend fun preprocessMessages(
		rounds: List<AgentContext.CompletedRound>,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		service: SettingService,
	): List<ChatMessage> = buildList {
		for (round in rounds) {
			add(
				convertUserMessage(
					round.userMessage, maxMessageChars, messageSummarizePrompt, summarizeModel, fallbackModels, service
				)
			)
			round.turns?.forEach { turn ->
				val toolCalls = turn.tools.map { tool ->
					ChatMessage.AssistantMessage.ToolCall(
						id = tool.callId,
						name = tool.name,
						arguments = tool.call.arguments,
					)
				}
				add(
					convertAssistantMessage(
						turn.assistantMessage,
						toolCalls,
						maxMessageChars,
						messageSummarizePrompt,
						summarizeModel,
						fallbackModels,
						service,
					)
				)
				turn.tools.forEach {
					add(
						convertToolMessage(
							it, maxMessageChars, messageSummarizePrompt, summarizeModel, fallbackModels, service,
						)
					)
				}
			}
			round.finalAssistantMessage?.let {
				add(
					convertAssistantMessage(
						it, null, maxMessageChars, messageSummarizePrompt, summarizeModel, fallbackModels, service,
					)
				)
			}
		}
	}
	
	private suspend fun convertUserMessage(
		msg: AgentContext.Message.User,
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		service: SettingService,
	): ChatMessage.UserMessage {
		val content = buildString {
			if (!msg.images.isNullOrEmpty()) {
				repeat(msg.images.size) { appendLine("<image_placeholder/>") }
			}
			append(msg.content)
		}
		val finalContent = if (content.length > maxMessageChars) {
			summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels, service)
		} else content
		return ChatMessage.UserMessage(finalContent, msg.timestamp)
	}
	
	private suspend fun convertAssistantMessage(
		msg: AgentContext.Message.Assistant,
		toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		service: SettingService,
	): ChatMessage.AssistantMessage {
		val content = msg.content ?: ""
		val finalContent = if (content.length > maxMessageChars) {
			summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels, service)
		} else content
		return ChatMessage.AssistantMessage(
			content = finalContent,
			createdAt = msg.timestamp,
			reasoningContent = msg.reasoning ?: "",
			toolCalls = toolCalls,
			model = msg.model.modelInfo.modelId,
		)
	}
	
	private suspend fun convertToolMessage(
		msg: AgentContext.Message.Tool,
		maxMessageChars: Int,
		messageSummarizePrompt: String,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		service: SettingService,
	): ChatMessage.ToolMessage {
		val content = msg.result.content
		val finalContent = if (content.length > maxMessageChars) {
			summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels, service)
		} else content
		return ChatMessage.ToolMessage(
			content = finalContent,
			createdAt = msg.result.timestamp,
			toolCallId = msg.callId,
		)
	}
	
	private suspend fun summarizeMessage(
		content: String,
		prompt: String,
		model: Model,
		fallbackModels: List<Model>?,
		service: SettingService,
	): String {
		val results = ResilientChat.execute(
			model = model,
			fallbackModels = fallbackModels,
			messages = listOf(
				ChatMessage.UserMessage(prompt.format(content), Clock.System.now()),
			),
			thinking = false,
			service = service,
		).toList()
		val success = results.filter { it.retrying == null }.map { it.result }
		return success.firstNotNullOfOrNull { it.message?.content } ?: content
	}
	
	private fun extractSummary(text: String): String {
		val match = Regex("<summary>([\\s\\S]*?)</summary>").find(text)
		return match?.groupValues?.get(1)?.trim() ?: text
	}
}
