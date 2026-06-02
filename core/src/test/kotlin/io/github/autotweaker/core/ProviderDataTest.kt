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

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.ProviderData
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProviderDataTest {
	
	private val testUrl = Url("https://api.example.com")
	
	// region ErrorHandlingRule
	
	@Test
	fun `ErrorHandlingRule constructs correctly`() {
		val rule = ProviderData.ErrorHandlingRule(503, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		assertEquals(503, rule.statusCode)
		assertEquals(ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY, rule.strategy)
	}
	
	@Test
	fun `ErrorHandlingRule with statusCode 100`() {
		val rule = ProviderData.ErrorHandlingRule(100, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		assertEquals(100, rule.statusCode)
	}
	
	@Test
	fun `ErrorHandlingRule with statusCode 599`() {
		val rule = ProviderData.ErrorHandlingRule(599, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		assertEquals(599, rule.statusCode)
	}
	
	@Test
	fun `ErrorHandlingRule with statusCode below 100 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ProviderData.ErrorHandlingRule(99, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		}
	}
	
	@Test
	fun `ErrorHandlingRule with statusCode above 599 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ProviderData.ErrorHandlingRule(600, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		}
	}
	
	@Test
	fun `ErrorHandlingRule with statusCode 0 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ProviderData.ErrorHandlingRule(0, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY)
		}
	}
	
	@Test
	fun `ErrorHandlingRule with negative statusCode throws`() {
		assertFailsWith<IllegalArgumentException> {
			ProviderData.ErrorHandlingRule(-1, ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK)
		}
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
	
	@Test
	fun `Provider with duplicate status codes throws`() {
		val rules = listOf(
			ProviderData.ErrorHandlingRule(429, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY),
			ProviderData.ErrorHandlingRule(429, ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK)
		)
		assertFailsWith<IllegalArgumentException> {
			ProviderData(
				id = UUID.randomUUID(),
				displayName = "dup",
				providerType = "openai",
				apiKey = UUID.randomUUID(),
				baseUrl = testUrl,
				errorHandlingRules = rules
			)
		}
	}
	
	@Test
	fun `Provider with multiple distinct status codes is valid`() {
		val rules = listOf(
			ProviderData.ErrorHandlingRule(429, ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY),
			ProviderData.ErrorHandlingRule(503, ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK),
			ProviderData.ErrorHandlingRule(500, ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK)
		)
		val providerData = ProviderData(
			id = UUID.randomUUID(),
			displayName = "multi",
			providerType = "openai",
			apiKey = UUID.randomUUID(),
			baseUrl = testUrl,
			errorHandlingRules = rules
		)
		assertEquals(3, providerData.errorHandlingRules.size)
	}
	
	// endregion
}
