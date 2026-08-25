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

package io.github.autotweaker.core.infrastructure.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmClientLoaderTest {
	
	@Test
	fun `load deepseek`() {
		val client = LlmClientLoader.load("deepseek")
		assertEquals("deepseek", client.providerInfo.name)
	}
	
	@Test
	fun `load mimo`() {
		val client = LlmClientLoader.load("mimo")
		assertEquals("mimo", client.providerInfo.name)
	}
	
	@Test
	fun `load invalid provider`() {
		val result = runCatching { LlmClientLoader.load("nonexistent") }
		assert(result.isFailure)
	}
	
	@Test
	fun `availableProviders returns registered providers`() {
		val providers = LlmClientLoader.available()
		assert(providers.contains("deepseek"))
		assert(providers.contains("mimo"))
		assert(providers.size >= 2)
	}
}
