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
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.AgentOutput
import io.github.autotweaker.api.types.agent.AgentOutput.Compact.Status
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.MessageBuilder
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.chat.inject
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.agent.runner.ContextManager
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.infrastructure.persist.db.session.MessageCacheImpl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.*

class CompactService(
	private val agentId: UUID,
	private val chat: ResilientChat,
	private val onOutput: (RuntimeOutput) -> Unit,
	private val cache: MessageCacheImpl,
	private val msg: MessageBuilder,
) : Loggable, Traceable {
	private val thinking = CompactSettings.Thinking().get()
	private val compactPrompt = CompactSettings.Prompt().get()
	
	suspend fun execute(
		model: AgentModel,
		ctx: ContextManager,
	) {
		val context = ctx.get()
		val rounds = context.historyRounds ?: return
		
		log.info(
			"Started compact  agentId={}  rounds={}  summarizeModel={}",
			agentId, rounds.size, model.summarize.id
		)
		
		val corrupted = cache.preload(
			buildSet { rounds.forEach { it.idsTo(this) } }
		)
		if (corrupted.isNotEmpty()) {
			log.warn("Failed to load messages for compact  agentId={}  corruptedMessages={}", agentId, corrupted.size)
			onOutput(
				RuntimeOutput.Output(
					AgentOutput.Error(
						"Compact failed: ${corrupted.size} corrupted messages could not be loaded",
						AgentOutput.Error.Type.COMPACT
					)
				)
			)
			return
		}
		
		val messages = rounds.toChatMessages().inject(
			null, context.compactedRounds?.summaryMessage?.getOrNull()?.content
		) + ChatMessage.User(compactPrompt.toContentPart())
		
		val maxRetries = CompactSettings.MaxCompactRetries().get()
		
		var attempt = 0
		var finalResult: AgentMessage.Compact?
		do {
			finalResult = runCompactRequest(
				model, messages
			)
			attempt++
		} while (finalResult == null && attempt < maxRetries)
		
		if (finalResult == null) {
			log.warn("Failed compact  agentId={}  attempts={}", agentId, attempt)
			
			onOutput(
				RuntimeOutput.Output(
					AgentOutput.Error(
						"Compact failed after $attempt attempts",
						AgentOutput.Error.Type.COMPACT
					)
				)
			)
			return
		}
		
		log.info(
			"Completed compact  agentId={}  roundCount={}  attempts={}  summaryLength={}",
			agentId, rounds.size, attempt, finalResult.content.length
		)
		
		ctx.applyCompact(finalResult.ref(), rounds)
	}
	
	private suspend fun runCompactRequest(
		model: AgentModel,
		messages: List<ChatMessage>,
	): AgentMessage.Compact? {
		val streamContent = StringBuilder()
		var lastResult: Pair<UUID, ChatMessage.Assistant>? = null
		var lastUsage: Usage? = null
		trace.catching {
			chat.execute(
				model = model.summarize,
				fallbackModels = model.fallback,
				messages = messages,
				stream = true,
				reasoning = ReasoningEffort(thinking)
			).collect { llmResult ->
				currentCoroutineContext().ensureActive()
				when (val result = llmResult.result) {
					is ChatResult.Chunk -> if (!result.content.isNullOrEmpty()) {
						streamContent.append(result.content)
						output(Status.OUTPUTTING, result.content!!, null)
					}
					
					is ChatResult.Assembled -> {
						result.usage?.let { lastUsage = it }
						lastResult = llmResult.model to result.message
					}
					
					else -> {}
				}
			}
		}.rethrowCancellation {
			log.debug("Cancelled compact  agentId={}", agentId)
		}.getOrElse { e ->
			log.warn("Failed compact request send  agentId={}  reason={}", agentId, e.message)
			output(Status.FAILED, streamContent.toString(), null)
			return null
		}
		
		val minSummaryLength = CompactSettings.MinSummaryLength().get()
		val extracted = lastResult?.second?.content?.extractSummary()?.takeIf { it.length >= minSummaryLength }
		
		if (extracted != null) {
			output(Status.FINISHED, extracted, lastUsage)
			return msg.compact(
				content = extracted,
				model = lastResult.first,
				usage = lastUsage
			)
		} else {
			log.warn("Found compact summary too short  agentId={}  content={}", agentId, lastResult?.second?.content)
			if (lastUsage != null && lastResult != null) onOutput(
				RuntimeOutput.UsageConsumed(
					UsageEntry(
						modelId = lastResult.first,
						timestamp = now(),
						usage = lastUsage
					)
				)
			)
			output(Status.FAILED, streamContent.toString(), lastUsage)
			return null
		}
	}
	
	private fun output(
		status: Status,
		content: String,
		usage: Usage?,
	) = onOutput(
		RuntimeOutput.Output(
			AgentOutput.Compact(
				status, content, usage
			)
		)
	)
	
	private fun List<AgentContextIndex.Round>.toChatMessages(): List<ChatMessage> = buildList {
		this@toChatMessages.forEach { round ->
			round.userMessage.onIntact {
				add(
					ChatMessage.User(
						content = it.content.inject().merge().toContentPart()
					)
				)
			}
			round.turns?.forEach { turn ->
				val calls = turn.tools.mapNotNull { tool ->
					tool.call.getOrNull()?.let {
						ChatMessage.Assistant.ToolCall(
							id = it.callId,
							name = it.callName,
							arguments = it.arguments
						)
					}
				}
				turn.assistantMessage.onIntact {
					add(
						ChatMessage.Assistant(
							reasoningContent = it.reasoning,
							content = it.content,
							toolCalls = calls.orNull()
						)
					)
				}
				turn.tools.forEach { tool ->
					tool.result.onIntact {
						add(
							ChatMessage.ToolResult(
								id = it.callId,
								content = it.content
							)
						)
					}
				}
			}
			round.assistantMessage?.onIntact {
				add(
					ChatMessage.Assistant(
						reasoningContent = it.reasoning,
						content = it.content
					)
				)
			}
		}
	}
	
	private fun String.extractSummary(): String =
		substringAfter("<summary>").substringBefore("</summary>").trim()
}
