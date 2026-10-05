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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.session.Msg
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.core.domain.agent.chat.inject
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.serialization.json.JsonElement

class DigestService(
	private val chat: ResilientChat,
	private val usageConsumed: (UsageEntry) -> Unit
) : Traceable, Loggable {
	suspend fun summary(prompt: String, context: RuntimeContext, model: AgentModel): JsonElement? {
		val messages = buildList {
			fun Msg<AgentMessage.User>.add() = onIntact {
				add(
					ChatMessage.User(
						content = it.content.content?.merge().orEmpty().toContentPart()
					)
				)
			}.discard()
			
			fun Msg<AgentMessage.Assistant>.add() = onIntact {
				add(
					ChatMessage.Assistant(
						reasoningContent = it.reasoning,
						content = it.content,
					)
				)
			}.discard()
			
			context.rounds().forEach { round ->
				round.userMessage.add()
				round.turns?.forEach { turn ->
					turn.assistantMessage.add()
				}
				round.assistantMessage?.add()
			}
			
			if (isEmpty()) return null
			
			add(
				ChatMessage.User(
					content = prompt.toContentPart()
				)
			)
		}.inject(null, context.compactedRounds?.summaryMessage?.getOrNull()?.content)
		
		var resultContent: String? = null
		
		trace.catching {
			chat.execute(
				model = model.summarize,
				fallbackModels = model.fallback,
				messages = messages,
				reasoning = ReasoningEffort(false),
				stream = false,
				jsonOutput = true
			).collect {
				val result = it.result
				if (result is ChatResult.Assembled) {
					result.usage?.let { usage ->
						usageConsumed(
							UsageEntry(
								modelId = it.model,
								usage = usage,
								timestamp = now()
							)
						)
					}
					resultContent = result.message.content
				}
				if (result is ChatResult.Failed) {
					log.warn(
						"Failed digest request  model={}  statusCode={}  reason={}  exception={}",
						it.model,
						result.statusCode,
						result.message,
						result.exception?.message()
					)
				}
			}
		}.ensureActive().onFailure {
			log.warn("Failed digest request send  model={}  reason={}", model.summarize.id, it.message)
		}
		
		return trace.catching {
			resultContent?.let {
				json.parseToJsonElement(it)
			}
		}.getOrNull()
	}
}
