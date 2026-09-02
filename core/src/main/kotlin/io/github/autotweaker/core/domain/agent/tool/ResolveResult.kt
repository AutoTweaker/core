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

package io.github.autotweaker.core.domain.agent.tool

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.tool.ToolPresentation
import kotlinx.serialization.json.JsonElement

sealed class ResolveResult {
	data class ParseFailure(
		val errorMessage: String,
		val presentation: ToolPresentation,
	) : ResolveResult()
	
	data class ResolveFailure(
		val toolName: String,
		val reason: String,
		val validatedArgs: JsonElement,
		val errorMessage: String,
		val presentation: ToolPresentation,
	) : ResolveResult()
	
	data class NeedsApproval(
		val toolName: String,
		val reason: String,
		val validatedArgs: JsonElement,
		val resolveResult: Tool.ResolveResult.Ready
	) : ResolveResult()
	
	data class Activation(
		val toolName: String,
		val reason: String,
		val validatedArgs: JsonElement,
		val presentation: ToolPresentation,
		val message: String,
	) : ResolveResult()
}
