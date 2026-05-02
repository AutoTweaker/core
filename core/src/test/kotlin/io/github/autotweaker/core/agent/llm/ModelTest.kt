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

package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.Price
import io.github.autotweaker.core.Provider.ErrorHandlingRule
import io.github.autotweaker.core.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.Provider.Model.*
import io.github.autotweaker.core.Provider.Model.TokenPrice.PriceTier
import io.github.autotweaker.core.Url
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
	
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	private val testModelInfo = ModelInfo(
		id = "test-model-id",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		price = TokenPrice(
			inputPrice = listOf(PriceTier(0, null, testPrice)),
			outputPrice = listOf(PriceTier(0, null, testPrice))
		),
		supportsStreaming = true,
		supportsToolCalls = true,
		supportsReasoning = true,
		supportsImage = false,
		supportsJsonOutput = true,
	)
	
	@Test
	fun `construct model with default config`() {
		val provider = Provider(
			name = "test-provider",
			baseUrl = testUrl,
			apiKey = "sk-test",
			errorHandlingRules = emptyList()
		)
		val model = Model(
			name = "test-model",
			provider = provider,
			modelInfo = testModelInfo,
		)
		assertEquals("test-model", model.name)
		assertEquals(provider, model.provider)
		assertEquals(testModelInfo, model.modelInfo)
		assertNull(model.config)
	}
	
	@Test
	fun `construct model with config`() {
		val provider = Provider("p", testUrl, "key", emptyList())
		val config = Config(temperature = 0.7, maxTokens = 2048, compactContextUsage = 0.8, compactTotalTokens = 0.9)
		val model = Model("m", provider, testModelInfo, config)
		assertEquals(config, model.config)
		assertEquals(0.7, model.config?.temperature)
		assertEquals(2048, model.config?.maxTokens)
	}
	
	@Test
	fun `construct provider with error handling rules`() {
		val rules = listOf(
			ErrorHandlingRule(429, RecoveryStrategy.RETRY),
			ErrorHandlingRule(503, RecoveryStrategy.FALLBACK),
			ErrorHandlingRule(400, RecoveryStrategy.CONTEXT_FALLBACK),
			ErrorHandlingRule(401, RecoveryStrategy.PROVIDER_FALLBACK),
		)
		val provider = Provider("p", testUrl, "key", rules)
		assertEquals(4, provider.errorHandlingRules.size)
		assertEquals(RecoveryStrategy.RETRY, provider.errorHandlingRules[0].strategy)
		assertEquals(429, provider.errorHandlingRules[0].statusCode)
	}
	
	@Test
	fun `model equality`() {
		val provider = Provider("p", testUrl, "key", emptyList())
		val model1 = Model("m", provider, testModelInfo)
		val model2 = Model("m", provider, testModelInfo)
		assertEquals(model1, model2)
		assertEquals(model1.hashCode(), model2.hashCode())
	}
	
	@Test
	fun `provider equality`() {
		val rules = listOf(ErrorHandlingRule(429, RecoveryStrategy.RETRY))
		val p1 = Provider("p", testUrl, "key", rules)
		val p2 = Provider("p", testUrl, "key", rules)
		assertEquals(p1, p2)
	}
	
	@Test
	fun `models with different names are not equal`() {
		val provider = Provider("p", testUrl, "key", emptyList())
		val model1 = Model("m1", provider, testModelInfo)
		val model2 = Model("m2", provider, testModelInfo)
		assertTrue(model1 != model2)
	}
}
