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

import java.math.BigDecimal
import java.util.*
import kotlin.test.*

class ProviderTest {
	
	private val testUrl = Url("https://api.example.com")
	
	private val testPrice = Price(BigDecimal("0.001"), Currency.getInstance("USD"), 1000)
	
	// region ErrorHandlingRule
	
	@Test
	fun `ErrorHandlingRule constructs correctly`() {
		val rule = Provider.ErrorHandlingRule(503, Provider.ErrorHandlingRule.RecoveryStrategy.RETRY)
		assertEquals(503, rule.statusCode)
		assertEquals(Provider.ErrorHandlingRule.RecoveryStrategy.RETRY, rule.strategy)
	}
	
	// endregion
	
	// region RecoveryStrategy enum
	
	@Test
	fun `RecoveryStrategy has all expected values`() {
		val values = Provider.ErrorHandlingRule.RecoveryStrategy.entries
		assertEquals(4, values.size)
		assertTrue(values.contains(Provider.ErrorHandlingRule.RecoveryStrategy.RETRY))
		assertTrue(values.contains(Provider.ErrorHandlingRule.RecoveryStrategy.FALLBACK))
		assertTrue(values.contains(Provider.ErrorHandlingRule.RecoveryStrategy.CONTEXT_FALLBACK))
		assertTrue(values.contains(Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK))
	}
	
	@Test
	fun `RecoveryStrategy valueOf works`() {
		assertEquals(
			Provider.ErrorHandlingRule.RecoveryStrategy.FALLBACK,
			Provider.ErrorHandlingRule.RecoveryStrategy.valueOf("FALLBACK")
		)
	}
	
	// endregion
	
	// region ModelInfo
	
	@Test
	fun `ModelInfo constructs with all fields`() {
		val tokenPrice = Provider.Model.TokenPrice(
			inputPrice = emptyList(),
			outputPrice = emptyList()
		)
		val info = Provider.Model.ModelInfo(
			id = "test-model",
			contextWindow = 128000,
			maxOutputTokens = 4096,
			price = tokenPrice,
			supportsStreaming = true,
			supportsToolCalls = true,
			supportsReasoning = true,
			supportsImage = false,
			supportsJsonOutput = true,
		)
		assertEquals("test-model", info.id)
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
		val tier = Provider.Model.TokenPrice.PriceTier(
			fromTokens = 0,
			toTokens = 1000000,
			price = testPrice,
			cachedPrice = null
		)
		val tokenPrice = Provider.Model.TokenPrice(
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
		val tier = Provider.Model.TokenPrice.PriceTier(
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
		val tier = Provider.Model.TokenPrice.PriceTier(
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
		val config = Provider.Model.Config(
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
		val config = Provider.Model.Config(null, null, null, null)
		assertNull(config.temperature)
		assertNull(config.maxTokens)
		assertNull(config.compactContextUsage)
		assertNull(config.compactTotalTokens)
	}
	
	// endregion
	
	// region Model
	
	@Test
	fun `Model constructs with all fields`() {
		val modelInfo = Provider.Model.ModelInfo(
			id = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
			price = Provider.Model.TokenPrice(emptyList(), emptyList()),
			supportsStreaming = true,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false,
		)
		val config = Provider.Model.Config(0.5, 1000, null, null)
		val model = Provider.Model(name = "gpt-4", modelInfo = modelInfo, config = config)
		assertEquals("gpt-4", model.name)
		assertEquals(modelInfo, model.modelInfo)
		assertEquals(config, model.config)
	}
	
	@Test
	fun `Model with null config`() {
		val modelInfo = Provider.Model.ModelInfo(
			id = "m2",
			contextWindow = 32000,
			maxOutputTokens = 1024,
			price = Provider.Model.TokenPrice(emptyList(), emptyList()),
			supportsStreaming = false,
			supportsToolCalls = false,
			supportsReasoning = false,
			supportsImage = false,
			supportsJsonOutput = false,
		)
		val model = Provider.Model(name = "basic", modelInfo = modelInfo, config = null)
		assertNull(model.config)
	}
	
	// endregion
	
	// region Provider
	
	@Test
	fun `Provider constructs with all fields`() {
		val modelInfo = Provider.Model.ModelInfo(
			id = "p1",
			contextWindow = 32000,
			maxOutputTokens = 2048,
			price = Provider.Model.TokenPrice(emptyList(), emptyList()),
			supportsStreaming = true,
			supportsToolCalls = true,
			supportsReasoning = true,
			supportsImage = true,
			supportsJsonOutput = true,
		)
		val model = Provider.Model(name = "premium", modelInfo = modelInfo, config = null)
		val rule = Provider.ErrorHandlingRule(429, Provider.ErrorHandlingRule.RecoveryStrategy.RETRY)
		val provider = Provider(
			name = "test-provider",
			providerType = "openai",
			apiKey = "sk-test",
			baseUrl = testUrl,
			models = listOf(model),
			errorHandlingRules = listOf(rule)
		)
		assertEquals("test-provider", provider.name)
		assertEquals("openai", provider.providerType)
		assertEquals("sk-test", provider.apiKey)
		assertEquals(testUrl, provider.baseUrl)
		assertEquals(1, provider.models.size)
		assertEquals(1, provider.errorHandlingRules.size)
	}
	
	@Test
	fun `Provider with empty models and rules`() {
		val provider = Provider(
			name = "empty",
			providerType = "custom",
			apiKey = "key",
			baseUrl = testUrl,
			models = emptyList(),
			errorHandlingRules = emptyList()
		)
		assertTrue(provider.models.isEmpty())
		assertTrue(provider.errorHandlingRules.isEmpty())
	}
	
	@Test
	fun `Provider data class equality`() {
		val p1 = Provider("p", "t", "k", testUrl, emptyList(), emptyList())
		val p2 = Provider("p", "t", "k", testUrl, emptyList(), emptyList())
		assertEquals(p1, p2)
		assertEquals(p1.hashCode(), p2.hashCode())
	}
	
	// endregion
}
