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

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.now
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.flow.toList
import java.util.*

class SummaryService(
	private val chat: ResilientChat,
	private val usageRepo: UsageRepository,
	private val agentId: UUID,
) : Traceable {
	suspend fun summarizeMessage(
		request: String,
		model: AgentModel,
		thinking: Boolean,
	): String? {
		val results = trace.catching {
			chat.execute(
				model = model.model,
				fallbackModels = model.fallback,
				messages = listOf(ChatMessage.User(request.toContentPart())),
				reasoning = ReasoningEffort(thinking)
			).toList()
		}.rethrowCancellation()
			.getOrElse { return null }
		
		val (lastResult, lastModel) = results.mapNotNull {
			(it.result as? ChatResult.Assembled ?: return@mapNotNull null) to it.model
		}.lastOrNull() ?: return null
		lastResult.usage?.let {
			usageRepo.save(
				UsageEntry(
					agentId = agentId,
					modelId = lastModel,
					timestamp = now(),
					usage = it
				)
			)
		}
		return lastResult.message.content
	}
}
