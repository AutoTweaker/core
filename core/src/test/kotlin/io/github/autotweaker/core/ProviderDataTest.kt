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

import io.github.autotweaker.api.types.Price
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import java.math.BigDecimal
import java.util.*
import kotlin.test.*

class ProviderDataTest {
	
	private val testUrl = Url("https://api.example.com")
	
	private val testPrice = Price(BigDecimal("0.001"), Currency.getInstance("USD"), 1000)
	
	// region ErrorHandlingRule
	
	@Test
	fun `ErrorHandlingRule constructs correctly`() {
		val rule = ProviderData.ErrorHandlingRule(503, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		assertEquals(503, rule.statusCode)
		assertEquals(ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY, rule.strategy)
	}
	
	// endregion
	
	// region RecoveryStrategy enum
	
	@Test
	fun `RecoveryStrategy has all expected values`() {
		val values = ProviderData.ErrorHandlingRule.RecoveryStrategy.entries
		assertEquals(4, values.size)
		assertTrue(values.contains(ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY))
		assertTrue(values.contains(ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK))
		assertTrue(values.contains(ProviderData.ErrorHandlingRule.RecoveryStrategy.CONTEXT_FALLBACK))
		assertTrue(values.contains(ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK))
	}
	
	@Test
	fun `RecoveryStrategy valueOf works`() {
		assertEquals(
			ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK,
			ProviderData.ErrorHandlingRule.RecoveryStrategy.valueOf("FALLBACK")
		)
	}
	
	// endregion
	
	// region ModelInfo
	
	@Test
	fun `ModelInfo constructs with all fields`() {
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = emptyList(),
			outputPrice = emptyList()
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
	
	// endregion
	
	// region TokenPrice
	
	@Test
	fun `TokenPrice constructs with tiers`() {
		val tier = ModelData.TokenPrice.PriceTier(
			fromTokens = 0,
			toTokens = 1000000,
			price = testPrice,
			cachedPrice = null
		)
		val tokenPrice = ModelData.TokenPrice(
			inputPrice = listOf(tier),
			outputPrice = listOf(tier)
		)
		assertEquals(1, tokenPrice.inputPrice.size)
		assertEquals(1, tokenPrice.outputPrice.size)
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
	
	// endregion
	
	// region ModelData
	
	@Test
	fun `Model constructs with all fields`() {
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
			price = ModelData.TokenPrice(emptyList(), emptyList()),
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
		val modelInfo = ModelData.ModelInfo(
			modelId = "m2",
			contextWindow = 32000,
			maxOutputTokens = 1024,
			price = ModelData.TokenPrice(emptyList(), emptyList()),
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
	
	// endregion
	
	// region ProviderData
	
	@Test
	fun `Provider constructs with all fields`() {
		val rule = ProviderData.ErrorHandlingRule(429, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		val apiKey = UUID.randomUUID()
		val providerId = UUID.randomUUID()
		val providerData = ProviderData(
			id = providerId,
			displayName = "test-provider",
			providerType = "openai",
			apiKey = apiKey,
			baseUrl = testUrl,
			errorHandlingRules = listOf(rule)
		)
		assertEquals(providerId, providerData.id)
		assertEquals("test-provider", providerData.displayName)
		assertEquals("openai", providerData.providerType)
		assertEquals(apiKey, providerData.apiKey)
		assertEquals(testUrl, providerData.baseUrl)
		assertEquals(1, providerData.errorHandlingRules.size)
	}
	
	@Test
	fun `Provider with empty rules`() {
		val apiKey = UUID.randomUUID()
		val providerData = ProviderData(
			id = UUID.randomUUID(),
			displayName = "empty",
			providerType = "custom",
			apiKey = apiKey,
			baseUrl = testUrl,
			errorHandlingRules = emptyList()
		)
		assertTrue(providerData.errorHandlingRules.isEmpty())
	}
	
	@Test
	fun `Provider data class equality`() {
		val id = UUID.randomUUID()
		val apiKey = UUID.randomUUID()
		val p1 = ProviderData(id, "p", "t", apiKey, testUrl, emptyList())
		val p2 = ProviderData(id, "p", "t", apiKey, testUrl, emptyList())
		assertEquals(p1, p2)
		assertEquals(p1.hashCode(), p2.hashCode())
	}
	
	// endregion
}
