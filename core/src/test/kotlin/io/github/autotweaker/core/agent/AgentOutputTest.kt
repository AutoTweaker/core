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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.github.autotweaker.core.tool.Tool
import io.ktor.http.*
import io.mockk.mockk
import kotlin.test.*
import kotlin.time.Clock

class AgentOutputTest {
	
	private val mockModel: Model = mockk(relaxed = true)
	
	// region StreamDelta
	
	@Test
	fun `StreamDelta with output content`() {
		val delta = AgentChatStreamResult.Delta(content = "hello", reasoningContent = null, toolCallFragments = null)
		val output = AgentOutput.StreamDelta(delta)
		assertEquals("hello", output.delta.content)
		assertNull(output.delta.reasoningContent)
	}
	
	@Test
	fun `StreamDelta with reasoning content`() {
		val delta =
			AgentChatStreamResult.Delta(content = null, reasoningContent = "thinking...", toolCallFragments = null)
		val output = AgentOutput.StreamDelta(delta)
		assertNull(output.delta.content)
		assertEquals("thinking...", output.delta.reasoningContent)
	}
	
	@Test
	fun `StreamDelta with tool call fragments`() {
		val fragments = listOf(ChatResult.ChunkToolCall(index = 0, id = "c1", name = "bash", arguments = "{}"))
		val delta = AgentChatStreamResult.Delta(content = null, reasoningContent = null, toolCallFragments = fragments)
		val output = AgentOutput.StreamDelta(delta)
		assertEquals(1, output.delta.toolCallFragments?.size)
		assertEquals("c1", output.delta.toolCallFragments?.get(0)?.id)
	}
	
	// endregion
	
	// region StreamError
	
	@Test
	fun `StreamError holds failing error`() {
		val error = AgentChatStreamResult.Failing.Error(
			content = "error",
			statusCode = HttpStatusCode.ServiceUnavailable,
			retrying = mockModel,
			timestamp = Clock.System.now(),
		)
		val output = AgentOutput.StreamError(error)
		assertEquals(error, output.error)
	}
	
	// endregion
	
	// region CompactOutput
	
	@Test
	fun `CompactOutput OUTPUTTING status`() {
		val output = AgentOutput.CompactOutput(
			AgentOutput.CompactOutput.Status.OUTPUTTING, "summarizing...", null
		)
		assertEquals(AgentOutput.CompactOutput.Status.OUTPUTTING, output.status)
		assertEquals("summarizing...", output.content)
	}
	
	@Test
	fun `CompactOutput FINISHED with usage`() {
		val usage = Usage(100, 50, 50)
		val output = AgentOutput.CompactOutput(
			AgentOutput.CompactOutput.Status.FINISHED, "summary done", usage
		)
		assertEquals(AgentOutput.CompactOutput.Status.FINISHED, output.status)
		assertEquals(usage, output.usage)
	}
	
	@Test
	fun `CompactOutput FAILED status`() {
		val output = AgentOutput.CompactOutput(
			AgentOutput.CompactOutput.Status.FAILED, "compact error", null
		)
		assertEquals(AgentOutput.CompactOutput.Status.FAILED, output.status)
	}
	
	// endregion
	
	// region ToolOutput
	
	@Test
	fun `ToolOutput holds name callId and content`() {
		val output = AgentOutput.ToolOutput("bash", "call-1", "command output")
		assertEquals("bash", output.name)
		assertEquals("call-1", output.callId)
		assertEquals("command output", output.content)
	}
	
	// endregion
	
	// region ToolCallRequest
	
	@Test
	fun `ToolCallRequest wraps pending tool calls`() {
		val pending = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				"c1", "bash_run", mockModel, """{"cmd":"ls"}""", "test", Clock.System.now()
			)
		)
		val output = AgentOutput.ToolCallRequest(pending)
		assertEquals(1, output.pendingToolCalls.size)
		assertEquals("c1", output.pendingToolCalls[0].callId)
	}
	
	@Test
	fun `ToolCallRequest empty pending list`() {
		val output = AgentOutput.ToolCallRequest(emptyList())
		assertTrue(output.pendingToolCalls.isEmpty())
	}
	
	// endregion
	
	// region ContextUpdate
	
	@Test
	fun `ContextUpdate with COMPACTED reason`() {
		val ctx = AgentContext(null, null, null, null, null)
		val output = AgentOutput.ContextUpdate(ctx, AgentOutput.ContextUpdate.UpdateReason.COMPACTED)
		assertEquals(ctx, output.context)
		assertEquals(AgentOutput.ContextUpdate.UpdateReason.COMPACTED, output.reason)
	}
	
	@Test
	fun `ContextUpdate with LLM reason`() {
		val ctx = AgentContext(null, null, null, null, null)
		val output = AgentOutput.ContextUpdate(ctx, AgentOutput.ContextUpdate.UpdateReason.LLM)
		assertEquals(AgentOutput.ContextUpdate.UpdateReason.LLM, output.reason)
	}
	
	@Test
	fun `ContextUpdate with null reason`() {
		val ctx = AgentContext(null, null, null, null, null)
		val output = AgentOutput.ContextUpdate(ctx, null)
		assertEquals(ctx, output.context)
		assertEquals(null, output.reason)
	}
	
	// endregion
	
	// region ToolListUpdate
	
	@Test
	fun `ToolListUpdate wraps active tools`() {
		val tools: List<Tool> = emptyList()
		val output = AgentOutput.ToolListUpdate(tools)
		assertTrue(output.activeTools.isEmpty())
	}
	
	// endregion
	
	// region Error
	
	@Test
	fun `Error LLM type`() {
		val output = AgentOutput.Error("llm error", AgentOutput.Error.Type.LLM)
		assertEquals("llm error", output.message)
		assertEquals(AgentOutput.Error.Type.LLM, output.type)
	}
	
	@Test
	fun `Error COMPACT type`() {
		val output = AgentOutput.Error("compact failed", AgentOutput.Error.Type.COMPACT)
		assertEquals("compact failed", output.message)
		assertEquals(AgentOutput.Error.Type.COMPACT, output.type)
	}
	
	// endregion
	
	// region sealed class hierarchy
	
	@Test
	fun `all variants are AgentOutput`() {
		assertIs<AgentOutput>(
			AgentOutput.StreamDelta(
				AgentChatStreamResult.Delta(
					content = "hi",
					reasoningContent = null,
					toolCallFragments = null
				)
			)
		)
		assertIs<AgentOutput>(
			AgentOutput.StreamError(
				AgentChatStreamResult.Failing.Error("e", HttpStatusCode.InternalServerError, null, Clock.System.now())
			)
		)
		assertIs<AgentOutput>(
			AgentOutput.CompactOutput(
				AgentOutput.CompactOutput.Status.OUTPUTTING, "x", null
			)
		)
		assertIs<AgentOutput>(AgentOutput.ToolOutput("t", "c", "o"))
		assertIs<AgentOutput>(AgentOutput.ToolCallRequest(emptyList()))
		assertIs<AgentOutput>(
			AgentOutput.ContextUpdate(
				AgentContext(null, null, null, null, null), null
			)
		)
		assertIs<AgentOutput>(AgentOutput.ToolListUpdate(emptyList()))
		assertIs<AgentOutput>(AgentOutput.Error("e", AgentOutput.Error.Type.LLM))
	}
	
	// endregion
}
