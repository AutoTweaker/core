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
import io.github.autotweaker.core.domain.model.Model

data class AgentModel(
	val model: Model,
	val summarize: Model,
	val compact: Model,
	val fallback: List<Model>?,
	val thinking: Boolean,
) {
	companion object {
		fun AgentModel.all(): List<Model> = buildList {
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
			thinking = thinking
		)
	}
}
