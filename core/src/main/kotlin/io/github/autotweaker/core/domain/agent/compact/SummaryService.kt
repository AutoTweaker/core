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
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.getOrElse
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentModel.Companion.all
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.flow.toList
import java.util.*
import kotlin.time.Clock

object SummaryService : Traceable {
	suspend fun summarizeMessage(
		content: String,
		prompt: String,
		model: AgentModel,
		thinkingEnabled: Boolean,
	): Pair<String, UsageSnapshot?> {
		val results = trace.catching {
			ResilientChat.execute(
				model = model.model,
				fallbackModels = model.fallback,
				messages = listOf(ChatMessage.UserMessage(prompt.format(content), Clock.System.now())),
				thinking = thinkingEnabled,
			).toList()
		}.rethrowCancellation()
			.getOrElse { return content to null }
		
		val success = results
			.filter { it.result is ChatResult.Assembled }
			.filter { it.result.message !is ChatMessage.ErrorMessage }
		val lastest = success.lastOrNull() ?: return content to null
		val snapshot = lastest.result.usage
			?.let { UsageSnapshot(it, model.findModelInfo(lastest.model)) }
		return (lastest.result.message?.content ?: content) to snapshot
	}
	
	fun AgentModel.findModelInfo(modelId: UUID) =
		all().find { it.id == modelId }?.modelInfo
			?: model.modelInfo
}
