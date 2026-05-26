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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class SummarizeServiceImplTest {
	private val mockModel = Model(
		provider = Provider(UUID.randomUUID(), "test-provider", mockk(relaxed = true), "key", emptyList()),
		modelInfo = mockk(relaxed = true),
		id = UUID.randomUUID(),
	)
	
	private fun env(model: Model = mockModel, fallbacks: List<Model>? = null): AgentEnvironment {
		val e = mockk<AgentEnvironment>()
		every { e.summarizeModel } returns model
		every { e.currentFallbackModels } returns fallbacks
		coEvery { e.emitOutput(any()) } returns Unit
		return e
	}
	
	@Test
	fun `summarize returns response on success`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(
				result = ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("summarized content", Clock.System.now()),
				),
				model = UUID.fromString("00000000-0000-0000-0000-000000000000"),
			)
		)
		
		val service = SummarizeServiceImpl(env())
		val result = service.summarize("long content", "summarize this")
		
		assertEquals("summarized content", result)
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize uses fallback models`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(
				result = ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("result", Clock.System.now()),
				),
				model = mockModel.id,
			)
		)
		
		val fbModel = Model(
			provider = Provider(UUID.randomUUID(), "fb", mockk(relaxed = true), "key2", emptyList()),
			modelInfo = mockk(relaxed = true),
			id = UUID.randomUUID(),
		)
		val service = SummarizeServiceImpl(env(fallbacks = listOf(fbModel)))
		val result = service.summarize("long content", "summarize this")
		
		assertEquals("result", result)
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize with error message throws`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(
				result = ChatResult.Assembled(
					message = ChatMessage.ErrorMessage("API error", Clock.System.now(), 500),
				),
				model = mockModel.id,
			),
		)
		
		val service = SummarizeServiceImpl(env())
		assertFailsWith<IllegalStateException> { service.summarize("content", "prompt") }
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize throws when empty response`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf()
		
		val service = SummarizeServiceImpl(env())
		assertFailsWith<IllegalStateException> { service.summarize("content", "prompt") }
		
		unmockkObject(ResilientChat)
	}
}
