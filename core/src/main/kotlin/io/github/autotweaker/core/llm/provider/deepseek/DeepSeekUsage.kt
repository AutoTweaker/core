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
data class DeepSeekUsage(
	//基本统计
	@SerialName("completion_tokens")
	val completionTokens: Int,
	@SerialName("prompt_tokens")
	val promptTokens: Int,
	@SerialName("total_tokens")
	val totalTokens: Int,
	
	//缓存统计
	@SerialName("prompt_cache_hit_tokens")
	val promptCacheHitTokens: Int? = null,
	@SerialName("prompt_cache_miss_tokens")
	val promptCacheMissTokens: Int? = null,
	
	//详细统计
	@SerialName("completion_tokens_details")
	val completionTokensDetails: CompletionTokensDetails? = null,
	@SerialName("prompt_tokens_details")
	val promptTokensDetails: PromptTokensDetails? = null
) {
	@Serializable
	data class CompletionTokensDetails(
		@SerialName("reasoning_tokens")
		val reasoningTokens: Int? = null
	)
	
	@Serializable
	data class PromptTokensDetails(
		@SerialName("cached_tokens")
		val cachedTokens: Int? = null
	)
}
