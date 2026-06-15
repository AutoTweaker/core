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

import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.compact.SummaryService
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import kotlin.time.Clock

class SummarizeServiceImpl(
	private val summarizeModel: Model,
	private val fallbackModels: List<Model>?,
	private val onOutput: suspend (AgentOutput) -> Unit,
) : SummarizeService {
	override suspend fun summarize(content: String, prompt: String): String {
		val (result, snapshot) = SummaryService.summarizeMessage(
			content = content,
			prompt = prompt,
			model = summarizeModel,
			fallbackModels = fallbackModels,
			thinkingEnabled = false,
		)
		snapshot?.let { onOutput(AgentOutput.UsageConsumed(Clock.System.now(), it.usage, it.model)) }
		return result
	}
}
