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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ToolMessageFactoryTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val timestamp = Clock.System.now()
	
	private fun presentation(text: String = "工具调用") = listOf(UiBlock.Text(text))
	
	private fun rawCall(id: String = "c1", name: String = "bash-run") =
		ChatMessage.AssistantMessage.ToolCall(id = id, name = name, arguments = """{"cmd":"echo"}""")
	
	private fun assistant() = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = "calling tools",
		modelId = UUID.randomUUID(),
		timestamp = timestamp,
		usage = null,
	)
	
	private fun result(
		parseFailures: List<Pair<ChatMessage.AssistantMessage.ToolCall, ResolveResult.ParseFailure>> = emptyList(),
		resolveFailures: List<Pair<ChatMessage.AssistantMessage.ToolCall, ResolveResult.ResolveFailure>> = emptyList(),
		activations: List<Pair<ChatMessage.AssistantMessage.ToolCall, ResolveResult.Activation>> = emptyList(),
	) = ThinkingStage.Result(assistant(), activations, parseFailures, resolveFailures, null)
	
	private fun pendingCall(
		callId: String = "c1",
		reason: String = "because",
		validatedArgs: JsonElement = JsonPrimitive("{}"),
		resolvedRequest: JsonElement = JsonPrimitive("""{"cmd":"echo"}"""),
	) = RuntimeContext.CurrentRound.PendingToolCall(
		id = UUID.randomUUID(),
		timestamp = timestamp,
		callId = callId,
		callName = "bash-run",
		arguments = """{"cmd":"echo","reason":"because"}""",
		reason = reason,
		validatedToolName = "bash",
		validatedArgs = validatedArgs,
		resolvedRequest = resolvedRequest,
		presentation = presentation(),
	)
	
	// region buildImmediateResults
	
	@Test
	fun `buildImmediateResults with empty inputs returns empty list`() {
		val results = ToolMessageFactory.buildImmediateResults(result())
		
		assertTrue(results.isEmpty())
	}
	
	@Test
	fun `buildImmediateResults parse failure keeps raw call fields`() {
		val call = rawCall()
		val failure = call to ResolveResult.ParseFailure("missing reason", presentation())
		
		val results = ToolMessageFactory.buildImmediateResults(
			result(parseFailures = listOf(failure))
		)
		
		assertEquals(1, results.size)
		val message = results[0]
		assertEquals("c1", message.callId)
		assertEquals(ToolResultStatus.FAILURE, message.result.status)
		assertEquals("missing reason", message.result.content)
		assertEquals(timestamp, message.result.timestamp)
		assertEquals("bash-run", message.call.callName)
		assertEquals(call.arguments, message.call.arguments)
		assertNull(message.call.reason)
		assertNull(message.call.validatedToolName)
		assertNull(message.call.validatedArgs)
		assertNull(message.call.resolvedRequest)
	}
	
	@Test
	fun `buildImmediateResults resolve failure carries validated fields`() {
		val call = rawCall()
		val failure = call to ResolveResult.ResolveFailure(
			toolName = "bash",
			reason = "no such file",
			validatedArgs = JsonPrimitive("{}"),
			errorMessage = "文件test.txt不存在或访问被拒绝",
			presentation = presentation(),
		)
		
		val results = ToolMessageFactory.buildImmediateResults(
			result(resolveFailures = listOf(failure))
		)
		
		assertEquals(1, results.size)
		val message = results[0]
		assertEquals(ToolResultStatus.FAILURE, message.result.status)
		assertEquals("文件test.txt不存在或访问被拒绝", message.result.content)
		assertEquals("no such file", message.call.reason)
		assertEquals("bash", message.call.validatedToolName)
		assertEquals(JsonPrimitive("{}"), message.call.validatedArgs)
		assertNull(message.call.resolvedRequest)
	}
	
	@Test
	fun `buildImmediateResults activation returns success with message`() {
		val call = rawCall()
		val activation = call to ResolveResult.Activation("tool activated", presentation())
		
		val results = ToolMessageFactory.buildImmediateResults(
			result(activations = listOf(activation))
		)
		
		assertEquals(1, results.size)
		val message = results[0]
		assertEquals(ToolResultStatus.SUCCESS, message.result.status)
		assertEquals("tool activated", message.result.content)
		assertEquals(timestamp, message.result.timestamp)
	}
	
	@Test
	fun `buildImmediateResults preserves order parse then resolve then activation`() {
		val call1 = rawCall("c1")
		val call2 = rawCall("c2")
		val call3 = rawCall("c3")
		val parse = call1 to ResolveResult.ParseFailure("parse error", presentation())
		val resolve = call2 to ResolveResult.ResolveFailure(
			"bash", "rejected", JsonPrimitive("{}"), "resolve error", presentation()
		)
		val activation = call3 to ResolveResult.Activation("activated", presentation())
		
		val results = ToolMessageFactory.buildImmediateResults(
			result(parseFailures = listOf(parse), resolveFailures = listOf(resolve), activations = listOf(activation))
		)
		
		assertEquals(3, results.size)
		assertEquals("parse error", results[0].result.content)
		assertEquals(ToolResultStatus.FAILURE, results[0].result.status)
		assertEquals("resolve error", results[1].result.content)
		assertEquals(ToolResultStatus.FAILURE, results[1].result.status)
		assertEquals("activated", results[2].result.content)
		assertEquals(ToolResultStatus.SUCCESS, results[2].result.status)
	}
	
	// endregion
	
	// region buildRejected
	
	@Test
	fun `buildRejected with reason formats feedback message`() {
		val call = pendingCall(reason = "invalid path")
		
		val message = ToolMessageFactory.buildRejected(call, "invalid path", presentation())
		
		assertEquals(ToolResultStatus.REJECTED, message.result.status)
		assertTrue(message.result.content.contains("invalid path"))
		assertNull(message.result.data)
	}
	
	@Test
	fun `buildRejected without reason uses default message`() {
		val message = ToolMessageFactory.buildRejected(pendingCall(), null, presentation())
		
		assertEquals(ToolResultStatus.REJECTED, message.result.status)
		assertTrue(message.result.content.contains("工具调用已被用户拒绝"))
	}
	
	@Test
	fun `buildRejected maps pending call fields`() {
		val call = pendingCall()
		val message = ToolMessageFactory.buildRejected(call, null, presentation())
		
		assertEquals(call.callId, message.callId)
		assertEquals(call.callName, message.call.callName)
		assertEquals(call.arguments, message.call.arguments)
		assertEquals(call.reason, message.call.reason)
		assertEquals(call.validatedToolName, message.call.validatedToolName)
		assertEquals(call.validatedArgs, message.call.validatedArgs)
		assertEquals(call.resolvedRequest, message.call.resolvedRequest)
		assertEquals(call.timestamp, message.call.timestamp)
		assertEquals(call.presentation, message.call.presentation)
	}
	
	// endregion
	
	// region buildParseError/buildResolveError
	
	@Test
	fun `buildParseError produces raw failure call`() {
		val call = rawCall()
		
		val message = ToolMessageFactory.buildParseError(timestamp, call, "boom", presentation())
		
		assertEquals(ToolResultStatus.FAILURE, message.result.status)
		assertEquals("boom", message.result.content)
		assertEquals(timestamp, message.result.timestamp)
		assertNull(message.call.validatedToolName)
		assertNull(message.call.validatedArgs)
	}
	
	@Test
	fun `buildResolveError carries validation info`() {
		val call = rawCall()
		
		val message = ToolMessageFactory.buildResolveError(
			timestamp, call, "no such file", "bash", JsonPrimitive("{}"), "boom", presentation()
		)
		
		assertEquals(ToolResultStatus.FAILURE, message.result.status)
		assertEquals("boom", message.result.content)
		assertEquals("no such file", message.call.reason)
		assertEquals("bash", message.call.validatedToolName)
		assertEquals(JsonPrimitive("{}"), message.call.validatedArgs)
		assertNull(message.call.resolvedRequest)
	}
	
	// endregion
	
	// region buildActivation
	
	@Test
	fun `buildActivation returns success with activation message`() {
		val call = rawCall()
		val message = ToolMessageFactory.buildActivation(
			timestamp, call to ResolveResult.Activation("please activate", presentation())
		)
		
		assertEquals(ToolResultStatus.SUCCESS, message.result.status)
		assertEquals("please activate", message.result.content)
		assertEquals(call.id, message.callId)
		assertEquals(timestamp, message.result.timestamp)
	}
	
	// endregion
}
