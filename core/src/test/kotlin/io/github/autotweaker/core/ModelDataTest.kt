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
import java.util.*
import kotlin.test.*

class ModelDataTest {
	
	// region ModelInfo
	
	@Test
	fun `ModelInfo constructs with all fields`() {
		val info = ModelData.ModelInfo(
			modelId = "test-model",
			contextWindow = 128000,
			maxOutputTokens = 4096,
			supportsStreaming = true,
			supportsToolCalls = true,
			supportsReasoning = true,
			supportsImage = false,
			supportsJsonOutput = true,
		)
		assertEquals("test-model", info.modelId)
		assertEquals(128000, info.contextWindow)
		assertEquals(4096, info.maxOutputTokens)
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
				supportsStreaming = true,
				supportsToolCalls = false,
				supportsReasoning = false,
				supportsImage = false,
				supportsJsonOutput = false
			)
		}
	}
	
	// endregion
	
	// region Config
	
	@Test
	fun `Config with all fields set`() {
		val config = ModelData.Config(
			temperature = 0.7,
			maxOutputTokens = 4096,
			compactContextUsage = 0.8,
			compactTotalTokens = 500000
		)
		assertEquals(0.7, config.temperature)
		assertEquals(4096, config.maxOutputTokens)
		assertEquals(0.8, config.compactContextUsage)
		assertEquals(500000, config.compactTotalTokens)
	}
	
	@Test
	fun `Config with null fields uses defaults`() {
		val config = ModelData.Config(null, null, null, null)
		assertNull(config.temperature)
		assertNull(config.maxOutputTokens)
		assertNull(config.compactContextUsage)
		assertNull(config.compactTotalTokens)
	}
	
	@Test
	fun `Config with temperature 0`() {
		val config =
			ModelData.Config(
				temperature = 0.0,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		assertEquals(0.0, config.temperature)
	}
	
	@Test
	fun `Config with temperature 2_0`() {
		val config =
			ModelData.Config(
				temperature = 2.0,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		assertEquals(2.0, config.temperature)
	}
	
	@Test
	fun `Config with temperature below 0 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = -0.1,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with temperature above 2_0 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = 2.1,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with zero maxTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = 0,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with negative maxTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = -1,
				compactContextUsage = null,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with zero compactContextUsage throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = 0.0,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with compactContextUsage above 1 throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = 1.1,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with compactContextUsage 1_0`() {
		val config =
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = 1.0,
				compactTotalTokens = null
			)
		assertEquals(1.0, config.compactContextUsage)
	}
	
	@Test
	fun `Config with negative compactContextUsage throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = -0.5,
				compactTotalTokens = null
			)
		}
	}
	
	@Test
	fun `Config with zero compactTotalTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = 0
			)
		}
	}
	
	@Test
	fun `Config with negative compactTotalTokens throws`() {
		assertFailsWith<IllegalArgumentException> {
			ModelData.Config(
				temperature = null,
				maxOutputTokens = null,
				compactContextUsage = null,
				compactTotalTokens = -100
			)
		}
	}
	
	// endregion
	
	// region ModelData
	
	@Test
	fun `Model constructs with all fields`() {
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
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
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
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
		val modelInfo = ModelData.ModelInfo(
			modelId = "m1",
			contextWindow = 64000,
			maxOutputTokens = 2048,
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
