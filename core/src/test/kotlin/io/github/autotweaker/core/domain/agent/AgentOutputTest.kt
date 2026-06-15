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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.session.ToolCallRequest
import io.github.autotweaker.api.types.tool.ToolOutput
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import io.github.autotweaker.core.domain.model.Model
import io.mockk.mockk
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentOutputTest {
	
	private val mockModel: Model = mockk(relaxed = true)
	
	// region StreamDelta
	
	@Test
	fun `StreamDelta with output content`() {
		val delta = StreamDelta(
			content = "hello",
			reasoningContent = null,
			toolCallFragments = null
		)
		val output = AgentOutput.LlmDelta(delta)
		assertEquals("hello", output.delta.content)
		assertNull(output.delta.reasoningContent)
	}
	
	@Test
	fun `StreamDelta with reasoning content`() {
		val delta = StreamDelta(
			content = null,
			reasoningContent = "thinking...",
			toolCallFragments = null
		)
		val output = AgentOutput.LlmDelta(delta)
		assertNull(output.delta.content)
		assertEquals("thinking...", output.delta.reasoningContent)
	}
	
	@Test
	fun `StreamDelta with tool call fragments`() {
		val fragments = listOf(ChatResult.ChunkToolCall(index = 0, id = "c1", name = "bash", arguments = "{}"))
		val delta = StreamDelta(
			content = null,
			reasoningContent = null,
			toolCallFragments = fragments
		)
		val output = AgentOutput.LlmDelta(delta)
		assertEquals(1, output.delta.toolCallFragments?.size)
		assertEquals("c1", output.delta.toolCallFragments?.get(0)?.id)
	}
	
	// endregion
	
	// region StreamError
	
	@Test
	fun `StreamError holds failing error`() {
		val error = AgentChatStreamResult.Failing.Error(
			content = "error",
			statusCode = 503,
			model = mockModel.id,
			timestamp = Clock.System.now(),
		)
		val output = AgentOutput.LlmError(error)
		assertEquals(error, output.error)
	}
	
	// endregion
	
	// region CompactOutput
	
	@Test
	fun `CompactOutput OUTPUTTING status`() {
		val output = AgentOutput.Compact(
			CompactOutput(status = CompactOutput.Status.OUTPUTTING, content = "summarizing...", usage = null)
		)
		assertEquals(CompactOutput.Status.OUTPUTTING, output.output.status)
		assertEquals("summarizing...", output.output.content)
	}
	
	@Test
	fun `CompactOutput FINISHED with usage`() {
		val usage = Usage(100, 50, 50)
		val output = AgentOutput.Compact(
			CompactOutput(status = CompactOutput.Status.FINISHED, content = "summary done", usage = usage)
		)
		assertEquals(CompactOutput.Status.FINISHED, output.output.status)
		assertEquals(usage, output.output.usage)
	}
	
	@Test
	fun `CompactOutput FAILED status`() {
		val output = AgentOutput.Compact(
			CompactOutput(status = CompactOutput.Status.FAILED, content = "compact error", usage = null)
		)
		assertEquals(CompactOutput.Status.FAILED, output.output.status)
	}
	
	// endregion
	
	// region ToolOutput
	
	@Test
	fun `ToolOutput holds name callId and content`() {
		val output = AgentOutput.Tool(ToolOutput(name = "bash", callId = "call-1", content = "command output"))
		assertEquals("bash", output.output.name)
		assertEquals("call-1", output.output.callId)
		assertEquals("command output", output.output.content)
	}
	
	// endregion
	
	// region ToolCallRequest
	
	@Test
	fun `ToolCallRequest wraps pending tool calls`() {
		val requests = listOf(
			ToolCallRequest(
				toolName = "bash_run",
				arguments = """{"cmd":"ls"}""",
				validatedArgs = null,
				reason = "test",
				callId = "call1"
			)
		)
		val output = AgentOutput.ToolRequest(requests)
		assertEquals(1, output.requests.size)
		assertEquals("bash_run", output.requests[0].toolName)
	}
	
	@Test
	fun `ToolCallRequest empty pending list`() {
		val output = AgentOutput.ToolRequest(emptyList())
		assertTrue(output.requests.isEmpty())
	}
	
	// endregion
	
	
	// region ToolListUpdate
	
	@Test
	fun `ToolListUpdate wraps active tools`() {
		val tools: List<String> = emptyList()
		val output = AgentOutput.ToolListUpdate(tools)
		assertTrue(output.activeTools.isEmpty())
	}
	
	// endregion
	
	// region Error
	
	@Test
	fun `Error LLM type`() {
		val output = AgentOutput.Error(AgentError(message = "llm error", type = AgentError.Type.LLM))
		assertEquals("llm error", output.error.message)
		assertEquals(AgentError.Type.LLM, output.error.type)
	}
	
	@Test
	fun `Error COMPACT type`() {
		val output = AgentOutput.Error(AgentError(message = "compact failed", type = AgentError.Type.COMPACT))
		assertEquals("compact failed", output.error.message)
		assertEquals(AgentError.Type.COMPACT, output.error.type)
	}
	
	// endregion
	
	// region sealed class hierarchy
	
	@Test
	fun `all variants are AgentOutput`() {
		assertIs<AgentOutput>(
			AgentOutput.LlmDelta(
				StreamDelta(
					content = "hi",
					reasoningContent = null,
					toolCallFragments = null
				)
			)
		)
		assertIs<AgentOutput>(
			AgentOutput.LlmError(
				AgentChatStreamResult.Failing.Error(
					"e",
					500,
					UUID.fromString("00000000-0000-0000-0000-000000000000"),
					Clock.System.now()
				)
			)
		)
		assertIs<AgentOutput>(
			AgentOutput.Compact(
				CompactOutput(status = CompactOutput.Status.OUTPUTTING, content = "x", usage = null)
			)
		)
		assertIs<AgentOutput>(AgentOutput.Tool(ToolOutput(name = "t", callId = "c", content = "o")))
		assertIs<AgentOutput>(AgentOutput.ToolRequest(emptyList()))
		assertIs<AgentOutput>(AgentOutput.ToolListUpdate(emptyList()))
		assertIs<AgentOutput>(AgentOutput.Error(AgentError(message = "e", type = AgentError.Type.LLM)))
	}
	
	// endregion
}
