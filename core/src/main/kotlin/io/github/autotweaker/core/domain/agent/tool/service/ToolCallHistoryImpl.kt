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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.trace
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.agent.tool.Tools.Companion.name
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.serialization.KSerializer

class ToolCallHistoryImpl(
	private val context: RuntimeContext,
) : ToolCallHistory, Traceable {
	@Suppress("NestedLambdaShadowedImplicitParameter")
	override suspend fun <Args : ToolArgs> getAll(
		self: Tool<Args>,
		argsSerializer: KSerializer<Args>
	): List<ToolCallHistory.Entry> = buildList {
		suspend fun RuntimeContext.Message.Tool.tryDeserialize() =
			call.validatedArgs?.let {
				trace.catching { Tools.deserializeValidatedArgs(self.name(), it) }.getOrNull()
			}?.let { add(ToolCallHistory.Entry(it, result.content)) }
		
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
	}
}
