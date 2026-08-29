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

import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.llm.ReasoningEffort

data class AgentModel(
	val model: RuntimeModel,
	val reasoning: ReasoningEffort?,
	val summarize: RuntimeModel,
	val compact: RuntimeModel,
	val fallback: List<RuntimeModel>?,
) {
	companion object {
		fun AgentModel.all(): List<RuntimeModel> = buildList {
			add(model)
			add(summarize)
			add(compact)
			fallback?.let { addAll(it) }
		}
		
		fun AgentModel.toModelConfig(): ModelConfig = ModelConfig(
			model = model.id,
			summarize = summarize.id,
			compact = compact.id,
			fallback = fallback?.map { it.id }.orEmpty(),
			reasoning = reasoning
		)
	}
}
