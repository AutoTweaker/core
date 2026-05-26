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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock

internal class SummarizeServiceImpl(
	private val env: AgentEnvironment,
) : SummarizeService {
	override suspend fun summarize(content: String, prompt: String): String {
		val results = ResilientChat.execute(
			model = env.summarizeModel,
			fallbackModels = env.currentFallbackModels,
			messages = listOf(
				ChatMessage.SystemMessage(prompt, Clock.System.now()),
				ChatMessage.UserMessage(content, Clock.System.now()),
			),
		).toList()
		val success = results.filter { it.result.message !is ChatMessage.ErrorMessage }.map { it.result }
		val finalResult = success.lastOrNull()
		finalResult?.usage?.let {
			env.emitOutput(
				AgentOutput.UsageConsumed(Clock.System.now(), it, env.summarizeModel.modelInfo)
			)
		}
		return finalResult?.message?.content ?: throw IllegalStateException("No response from LLM")
	}
}
