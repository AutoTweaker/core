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
	init {
		require(displayName.isNotBlank()) { "displayName must not be blank" }
	}
	
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
	) {
		init {
			require(modelId.isNotBlank()) { "modelId must not be blank" }
			require(contextWindow > 0) { "contextWindow must be > 0, got $contextWindow" }
			require(maxOutputTokens > 0) { "maxOutputTokens must be > 0, got $maxOutputTokens" }
		}
	}
	
	@Serializable
	data class TokenPrice(
		val inputPrice: List<PriceTier>,
		val outputPrice: List<PriceTier>,
	) {
		init {
			require(inputPrice.isNotEmpty()) { "inputPrice must not be empty" }
			require(outputPrice.isNotEmpty()) { "outputPrice must not be empty" }
			requireValidTiers(inputPrice, "inputPrice")
			requireValidTiers(outputPrice, "outputPrice")
		}
		
		companion object {
			private fun requireValidTiers(tiers: List<PriceTier>, name: String) {
				val sorted = tiers.sortedBy { it.fromTokens }
				require(sorted.first().fromTokens == 0) { "$name must start from 0, got fromTokens=${sorted.first().fromTokens}" }
				require(sorted.last().toTokens == null) { "$name last tier must have no upper bound (toTokens=null)" }
				sorted.zipWithNext().forEach { (prev, next) ->
					require(prev.toTokens == next.fromTokens) {
						"$name gap or overlap: tier ending at ${prev.toTokens} not connected to next tier starting at ${next.fromTokens}"
					}
				}
			}
		}
		
		@Serializable
		data class PriceTier(
			val fromTokens: Int, val toTokens: Int? = null, val price: Price, val cachedPrice: Price? = null
		) {
			init {
				require(fromTokens >= 0) { "fromTokens must be >= 0, got $fromTokens" }
				require(toTokens == null || toTokens > fromTokens) { "toTokens must be > fromTokens ($fromTokens), got $toTokens" }
			}
		}
	}
	
	@Serializable
	data class Config(
		val temperature: Double?,
		val maxTokens: Int?,
		val compactContextUsage: Double?,
		val compactTotalTokens: Int?,
	) {
		init {
			require(temperature == null || temperature in 0.0..2.0) { "temperature must be in [0.0, 2.0], got $temperature" }
			require(maxTokens == null || maxTokens > 0) { "maxTokens must be > 0, got $maxTokens" }
			require(compactContextUsage == null || compactContextUsage > 0.0 && compactContextUsage <= 1.0) { "compactContextUsage must be in (0.0, 1.0], got $compactContextUsage" }
			require(compactTotalTokens == null || compactTotalTokens > 0) { "compactTotalTokens must be > 0, got $compactTotalTokens" }
		}
	}
}
