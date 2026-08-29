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

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class ToolCallHistoryImpl(
	private val context: RuntimeContext,
) : ToolCallHistory, Traceable {
	@Suppress("NestedLambdaShadowedImplicitParameter")
	override fun <Request, Result> getAll(
		requestSerializer: KSerializer<Request>,
		resultSerializer: KSerializer<Result>,
	): PairList<Request, Result> = buildList {
		fun RuntimeContext.Message.Tool.tryDeserialize() {
			val request = call.resolvedRequest ?: return
			val result = result.data ?: return
			trace.catching {
				Pair(
					Json.decodeFromJsonElement(requestSerializer, request),
					Json.decodeFromJsonElement(resultSerializer, result)
				)
			}.getOrNull()?.let { add(it) }
		}
		
		context.historyRounds?.forEach {
			it.turns?.forEach {
				it.tools.forEach {
					it.tryDeserialize()
				}
			}
		}
		
		context.currentRound?.turns?.forEach {
			it.tools.forEach {
				it.tryDeserialize()
			}
		}
		
		context.currentRound?.finishedToolCalls?.forEach {
			it.tryDeserialize()
		}
	}
}
