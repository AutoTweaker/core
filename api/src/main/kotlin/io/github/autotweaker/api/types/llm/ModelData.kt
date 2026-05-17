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

import io.github.autotweaker.api.types.Price
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ModelData(
	@Serializable(with = UuidSerializer::class) val id: UUID,
	val displayName: String,
	val modelInfo: ModelInfo,
	@Serializable(with = UuidSerializer::class) val providerId: UUID,
	val config: Config? = null,
) {
	@Serializable
	data class ModelInfo(
		val modelId: String,
		
		val contextWindow: Int,
		val maxOutputTokens: Int,
		val price: TokenPrice,
		
		val supportsStreaming: Boolean,
		val supportsToolCalls: Boolean,
		val supportsReasoning: Boolean,
		val supportsImage: Boolean,
		val supportsJsonOutput: Boolean,
	)
	
	@Serializable
	data class TokenPrice(
		val inputPrice: List<PriceTier>,
		val outputPrice: List<PriceTier>,
	) {
		@Serializable
		data class PriceTier(
			val fromTokens: Int, val toTokens: Int? = null, val price: Price, val cachedPrice: Price? = null
		)
	}
	
	@Serializable
	data class Config(
		val temperature: Double?,
		val maxTokens: Int?,
		val compactContextUsage: Double?,
		val compactTotalTokens: Double?,
	)
}