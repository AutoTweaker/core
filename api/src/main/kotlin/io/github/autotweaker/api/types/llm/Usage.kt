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

package io.github.autotweaker.api.types.llm

import kotlinx.serialization.Serializable

@Serializable
data class Usage(
	val totalTokens: Int, val promptTokens: Int, val completionTokens: Int,
	
	val reasoningTokens: Int? = null, val cacheHitTokens: Int? = null, val cacheMissTokens: Int? = null,
	
	val imageTokens: Int? = null
) {
	operator fun plus(other: Usage): Usage = Usage(
		totalTokens = totalTokens + other.totalTokens,
		promptTokens = promptTokens + other.promptTokens,
		completionTokens = completionTokens + other.completionTokens,
		reasoningTokens = merge(reasoningTokens, other.reasoningTokens),
		cacheHitTokens = merge(cacheHitTokens, other.cacheHitTokens),
		cacheMissTokens = merge(cacheMissTokens, other.cacheMissTokens),
		imageTokens = merge(imageTokens, other.imageTokens),
	)
	
	private fun merge(a: Int?, b: Int?): Int? = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
}
