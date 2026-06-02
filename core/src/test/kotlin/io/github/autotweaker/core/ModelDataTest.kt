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

package io.github.autotweaker.core

import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.Price
import java.math.BigDecimal
import java.util.*
import kotlin.test.*

class ModelDataTest {
	
	private val testPrice = Price(BigDecimal("0.001"), Currency.getInstance("USD"), 1000)
	
	// region ModelInfo
	
	@Test
	fun `ModelInfo constructs with all fields`() {
		val defaultTier = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = defaultTier,
			outputPrice = defaultTier
		)
		val info = ModelData.ModelInfo(
			modelId = "test-model",
			contextWindow = 128000,
			maxOutputTokens = 4096,
			price = tokenPrice,
			supportsStreaming = true,
			supportsToolCalls = true,
			supportsReasoning = true,
			supportsImage = false,
			supportsJsonOutput = true,
		)
		assertEquals("test-model", info.modelId)
		assertEquals(128000, info.contextWindow)
		assertEquals(4096, info.maxOutputTokens)
		assertEquals(tokenPrice, info.price)
		assertTrue(info.supportsStreaming)
		assertTrue(info.supportsToolCalls)
		assertTrue(info.supportsReasoning)
		assertFalse(info.supportsImage)
		assertTrue(info.supportsJsonOutput)
	}
	
	@Test
	fun `ModelInfo with blank modelId throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "   ",
				contextWindow = 128000,
				maxOutputTokens = 4096,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	@Test
	fun `ModelInfo with empty modelId throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "",
				contextWindow = 128000,
				maxOutputTokens = 4096,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	@Test
	fun `ModelInfo with zero contextWindow throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "test",
				contextWindow = 0,
				maxOutputTokens = 4096,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	@Test
	fun `ModelInfo with negative contextWindow throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "test",
				contextWindow = -1,
				maxOutputTokens = 4096,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	@Test
	fun `ModelInfo with zero maxOutputTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "test",
				contextWindow = 128000,
				maxOutputTokens = 0,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	@Test
	fun `ModelInfo with negative maxOutputTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.ModelInfo(
				modelId = "test",
				contextWindow = 128000,
				maxOutputTokens = -100,
				price = ModelData.TokenPrice(
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)),
					listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
				),
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	// endregion
	
	// region TokenPrice
	
	@Test
	fun `TokenPrice constructs with single unbounded tier`() {
		val tier = ModelData.TokenPrice.PriceTier(0, null, testPrice)
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = listOf(tier),
			outputPrice = listOf(tier)
		)
		assertEquals(1, tokenPrice.inputPrice.size)
		assertEquals(1, tokenPrice.outputPrice.size)
	}
	
	@Test
	fun `TokenPrice constructs with connected tiers`() {
		val tier1 = ModelData.TokenPrice.PriceTier(0, 256000, testPrice)
		val tier2 = ModelData.TokenPrice.PriceTier(256000, null, testPrice)
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = listOf(tier1, tier2),
			outputPrice = listOf(tier1, tier2)
		)
		assertEquals(2, tokenPrice.inputPrice.size)
	}
	
	@Test
	fun `TokenPrice with empty inputPrice throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(emptyList(), listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)))
		}
	}
	
	@Test
	fun `TokenPrice with empty outputPrice throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)), emptyList())
		}
	}
	
	@Test
	fun `TokenPrice with gap between tiers throws`() {
		val tier1 = ModelData.TokenPrice.PriceTier(0, 100000, testPrice)
		val tier2 = ModelData.TokenPrice.PriceTier(200000, null, testPrice)
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(listOf(tier1, tier2), listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)))
		}
	}
	
	@Test
	fun `TokenPrice with overlap between tiers throws`() {
		val tier1 = ModelData.TokenPrice.PriceTier(0, 300000, testPrice)
		val tier2 = ModelData.TokenPrice.PriceTier(200000, null, testPrice)
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(listOf(tier1, tier2), listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)))
		}
	}
	
	@Test
	fun `TokenPrice not starting from 0 throws`() {
		val tier = ModelData.TokenPrice.PriceTier(100, null, testPrice)
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(listOf(tier), listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)))
		}
	}
	
	@Test
	fun `TokenPrice last tier with toTokens set throws`() {
		val tier = ModelData.TokenPrice.PriceTier(0, 1000000, testPrice)
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice(listOf(tier), listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice)))
		}
	}
	
	@Test
	fun `TokenPrice tiers auto-sorted by fromTokens`() {
		val tier1 = ModelData.TokenPrice.PriceTier(256000, null, testPrice)
		val tier2 = ModelData.TokenPrice.PriceTier(0, 256000, testPrice)
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = listOf(tier1, tier2),
			outputPrice = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		)
		assertEquals(2, tokenPrice.inputPrice.size)
	}
	
	// endregion
	
	// region PriceTier
	
	@Test
	fun `PriceTier with all fields`() {
		val cachedPrice = Price(BigDecimal("0.0005"), Currency.getInstance("USD"), 1000)
		val tier = ModelData.TokenPrice.PriceTier(
			fromTokens = 0,
			toTokens = 1000000,
			price = testPrice,
			cachedPrice = cachedPrice
		)
		assertEquals(0, tier.fromTokens)
		assertEquals(1000000, tier.toTokens)
		assertEquals(testPrice, tier.price)
		assertEquals(cachedPrice, tier.cachedPrice)
	}
	
	@Test
	fun `PriceTier with null cachedPrice and toTokens`() {
		val tier = ModelData.TokenPrice.PriceTier(
			fromTokens = 500000,
			toTokens = null,
			price = testPrice,
			cachedPrice = null
		)
		assertEquals(500000, tier.fromTokens)
		assertNull(tier.toTokens)
		assertNull(tier.cachedPrice)
	}
	
	@Test
	fun `PriceTier with negative fromTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice.PriceTier(-1, null, testPrice)
		}
	}
	
	@Test
	fun `PriceTier with toTokens equal to fromTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice.PriceTier(100, 100, testPrice)
		}
	}
	
	@Test
	fun `PriceTier with toTokens less than fromTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.TokenPrice.PriceTier(500, 100, testPrice)
		}
	}
	
	@Test
	fun `PriceTier with fromTokens zero and toTokens null is valid`() {
		val tier = ModelData.TokenPrice.PriceTier(0, null, testPrice)
		assertEquals(0, tier.fromTokens)
		assertNull(tier.toTokens)
	}
	
	// endregion
	
	// region Config
	
	@Test
	fun `Config with all fields set`() {
		val config = ModelData.Config(
			temperature = 0.7,
			maxTokens = 4096,
			compactContextUsage = 0.8,
			compactTotalTokens = 0.5
		)
		assertEquals(0.7, config.temperature)
		assertEquals(4096, config.maxTokens)
		assertEquals(0.8, config.compactContextUsage)
		assertEquals(0.5, config.compactTotalTokens)
	}
	
	@Test
	fun `Config with null fields uses defaults`() {
		val config = ModelData.Config(null, null, null, null)
		assertNull(config.temperature)
		assertNull(config.maxTokens)
		assertNull(config.compactContextUsage)
		assertNull(config.compactTotalTokens)
	}
	
	@Test
	fun `Config with temperature 0`() {
		val config =
			ModelData.Config(temperature = 0.0, maxTokens = null, compactContextUsage = null, compactTotalTokens = null)
		assertEquals(0.0, config.temperature)
	}
	
	@Test
	fun `Config with temperature 2_0`() {
		val config =
			ModelData.Config(temperature = 2.0, maxTokens = null, compactContextUsage = null, compactTotalTokens = null)
		assertEquals(2.0, config.temperature)
	}
	
	@Test
	fun `Config with temperature below 0 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = -0.1,
				maxTokens = null,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with temperature above 2_0 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = 2.1, maxTokens = null, compactContextUsage = null, compactTotalTokens = null)
		}
	}
	
	@Test
	fun `Config with zero maxTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = null, maxTokens = 0, compactContextUsage = null, compactTotalTokens = null)
		}
	}
	
	@Test
	fun `Config with negative maxTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = null, maxTokens = -1, compactContextUsage = null, compactTotalTokens = null)
		}
	}
	
	@Test
	fun `Config with zero compactContextUsage throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = null, maxTokens = null, compactContextUsage = 0.0, compactTotalTokens = null)
		}
	}
	
	@Test
	fun `Config with compactContextUsage above 1 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = null, maxTokens = null, compactContextUsage = 1.1, compactTotalTokens = null)
		}
	}
	
	@Test
	fun `Config with compactContextUsage 1_0`() {
		val config =
			ModelData.Config(temperature = null, maxTokens = null, compactContextUsage = 1.0, compactTotalTokens = null)
		assertEquals(1.0, config.compactContextUsage)
	}
	
	@Test
	fun `Config with negative compactContextUsage throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxTokens = null,
				compactContextUsage = -0.5,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with zero compactTotalTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(temperature = null, maxTokens = null, compactContextUsage = null, compactTotalTokens = 0.0)
		}
	}
	
	@Test
	fun `Config with negative compactTotalTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxTokens = null,
				compactContextUsage = null,
				compactTotalTokens = -100.0
			)
		}
	}
	
	// endregion
	
	// region ModelData
	
	@Test
	fun `Model constructs with all fields`() {
		val defaultTier = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
			price = ModelData.TokenPrice(defaultTier, defaultTier),
			supportsStreaming = true,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false,
		)
		val config = ModelData.Config(0.5, 1000, null, null)
		val model = ModelData(
			id = UUID.randomUUID(),
			displayName = "gpt-4",
			modelInfo = modelInfo,
			config = config,
			providerId = UUID.randomUUID(),
		)
		assertEquals("gpt-4", model.displayName)
		assertEquals(modelInfo, model.modelInfo)
		assertEquals(config, model.config)
	}
	
	@Test
	fun `Model with null config`() {
		val defaultTier = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		val modelInfo = ModelData.ModelInfo(
			modelId = "m2",
			contextWindow = 32000,
			maxOutputTokens = 1024,
			price = ModelData.TokenPrice(defaultTier, defaultTier),
			supportsStreaming = false,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false,
		)
		val model = ModelData(
			id = UUID.randomUUID(),
			displayName = "basic",
			modelInfo = modelInfo,
			config = null,
			providerId = UUID.randomUUID(),
		)
		assertNull(model.config)
	}
	
	@Test
	fun `Model with blank displayName throws`() {
		val defaultTier = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
			price = ModelData.TokenPrice(defaultTier, defaultTier),
			supportsStreaming = true,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false
		)
		assertFailsWith<IllegalArgumentException> {
			ModelData(
				id = UUID.randomUUID(),
				displayName = "   ",
				modelInfo = modelInfo,
				providerId = UUID.randomUUID()
			)
		}
	}
	
	@Test
	fun `Model with empty displayName throws`() {
		val defaultTier = listOf(ModelData.TokenPrice.PriceTier(0, null, testPrice))
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
			price = ModelData.TokenPrice(defaultTier, defaultTier),
			supportsStreaming = true,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false
		)
		assertFailsWith<IllegalArgumentException> {
			ModelData(
				id = UUID.randomUUID(),
				displayName = "",
				modelInfo = modelInfo,
				providerId = UUID.randomUUID()
			)
		}
	}
	
	// endregion
}
