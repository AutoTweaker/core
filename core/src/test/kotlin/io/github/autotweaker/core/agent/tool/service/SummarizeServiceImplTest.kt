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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.agent.llm.ResilientChat
import io.github.autotweaker.core.agent.llm.ResilientChatResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject

import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class SummarizeServiceImplTest {
	private val mockService: SettingService = mockk(relaxed = true)
	
	private val mockModel = Model(
		provider = Provider("test-provider", mockk(relaxed = true), "key", emptyList()),
		modelInfo = mockk(relaxed = true),
		id = UUID.randomUUID(),
	)
	
	private val fallbackModel = Model(
		provider = Provider("fb-provider", mockk(relaxed = true), "key2", emptyList()),
		modelInfo = mockk(relaxed = true),
		id = UUID.randomUUID(),
	)
	
	@Test
	fun `summarize returns response on success`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("summarized content", Clock.System.now()),
				),
				retrying = null,
			)
		)
		
		val service = SummarizeServiceImpl(mockModel, service = mockService)
		val result = service.summarize("long content", "summarize this")
		
		assertEquals("summarized content", result)
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize skips retrying results and uses success`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult.Chunk(message = null),
				retrying = mockModel,
			),
			ResilientChatResult(
				result = ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("fallback result", Clock.System.now()),
				),
				retrying = null,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel), mockService)
		val result = service.summarize("content", "summarize")
		
		assertEquals("fallback result", result)
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize throws when no successful response`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult.Chunk(message = null),
				retrying = mockModel,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel), mockService)
		
		assertFailsWith<IllegalStateException> {
			service.summarize("content", "summarize")
		}
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize empty flow throws`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any()) } returns flowOf()
		
		val service = SummarizeServiceImpl(mockModel, service = mockService)
		
		assertFailsWith<IllegalStateException> {
			service.summarize("content", "summarize")
		}
		
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `summarize bypasses fallback when first model succeeds`() = runTest {
		mockkObject(ResilientChat)
		coEvery { ResilientChat.execute(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(
				result = ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("first success", Clock.System.now()),
				),
				retrying = null,
			),
		)
		
		val service = SummarizeServiceImpl(mockModel, listOf(fallbackModel), mockService)
		val result = service.summarize("content", "summarize")
		
		assertEquals("first success", result)
		
		unmockkObject(ResilientChat)
	}
}
