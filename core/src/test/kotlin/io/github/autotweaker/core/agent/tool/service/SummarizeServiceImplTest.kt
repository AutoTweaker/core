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

package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.agent.llm.ResilientChatResult
import io.github.autotweaker.core.agent.llm.resilientChat
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class SummarizeServiceImplTest {
	
	private val mockModel = Model(
		name = "summarizer",
		provider = Provider("test-provider", mockk(relaxed = true), "key", emptyList()),
		modelInfo = mockk(relaxed = true),
	)
	
	private val fallbackModel = Model(
		name = "fallback",
		provider = Provider("fb-provider", mockk(relaxed = true), "key2", emptyList()),
		modelInfo = mockk(relaxed = true),
	)
	
	@Test
	fun `summarize returns response on success`() = runTest {
		mockkStatic(::resilientChat)
		coEvery { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult(
					message = ChatMessage.AssistantMessage("summarized content", Clock.System.now()),
				),
				retrying = null,
			)
		)
		
		val service = SummarizeServiceImpl(mockModel)
		val result = service.summarize("long content", "summarize this")
		
		assertEquals("summarized content", result)
		
		unmockkStatic(::resilientChat)
	}
	
	@Test
	fun `summarize skips retrying results and uses success`() = runTest {
		mockkStatic(::resilientChat)
		coEvery { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult(message = null),
				retrying = mockModel,
			),
			ResilientChatResult(
				result = ChatResult(
					message = ChatMessage.AssistantMessage("fallback result", Clock.System.now()),
				),
				retrying = null,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel))
		val result = service.summarize("content", "summarize")
		
		assertEquals("fallback result", result)
		
		unmockkStatic(::resilientChat)
	}
	
	@Test
	fun `summarize throws when no successful response`() = runTest {
		mockkStatic(::resilientChat)
		coEvery { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult(message = null),
				retrying = mockModel,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel))
		
		assertFailsWith<IllegalStateException> {
			service.summarize("content", "summarize")
		}
		
		unmockkStatic(::resilientChat)
	}
	
	@Test
	fun `summarize empty flow throws`() = runTest {
		mockkStatic(::resilientChat)
		coEvery { resilientChat(any(), any(), any(), any()) } returns flowOf()
		
		val service = SummarizeServiceImpl(mockModel)
		
		assertFailsWith<IllegalStateException> {
			service.summarize("content", "summarize")
		}
		
		unmockkStatic(::resilientChat)
	}
	
	@Test
	fun `summarize bypasses fallback when first model succeeds`() = runTest {
		mockkStatic(::resilientChat)
		coEvery { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult(
					message = ChatMessage.AssistantMessage("first success", Clock.System.now()),
				),
				retrying = null,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel))
		val result = service.summarize("content", "summarize")
		
		assertEquals("first success", result)
		
		unmockkStatic(::resilientChat)
	}
}
