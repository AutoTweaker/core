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

package io.github.autotweaker.core.llm.provider.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeepSeekFinishReason(val value: String) {
	@SerialName("stop")
	STOP("stop"),
	
	@SerialName("length")
	LENGTH("length"),
	
	@SerialName("content_filter")
	CONTENT_FILTER("content_filter"),
	
	@SerialName("tool_calls")
	TOOL_CALLS("tool_calls"),
	
	@SerialName("insufficient_system_resource")
	INSUFFICIENT_SYSTEM_RESOURCE("insufficient_system_resource")
}
