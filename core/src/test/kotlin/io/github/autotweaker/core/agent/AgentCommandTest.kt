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

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import io.mockk.mockk
import kotlin.test.*

class AgentCommandTest {
	
	private val mockModel: Model = mockk(relaxed = true)
	
	// region Directive
	
	@Test
	fun `Stop is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Stop)
	}
	
	@Test
	fun `Pause is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Pause)
	}
	
	@Test
	fun `Resume is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Resume)
	}
	
	@Test
	fun `Cancel is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Cancel)
	}
	
	@Test
	fun `Retry is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Retry)
	}
	
	@Test
	fun `Compact is a data object singleton`() {
		assertNotNull(AgentCommand.Directive.Compact)
	}
	
	@Test
	fun `UpdateModel holds model and optional fields`() {
		val cmd = AgentCommand.Directive.UpdateModel(mockModel)
		assertEquals(mockModel, cmd.model)
		assertNull(cmd.fallbackModels)
		assertNull(cmd.thinking)
	}
	
	@Test
	fun `UpdateModel with all optional fields`() {
		val fallbacks = listOf(mockModel)
		val cmd = AgentCommand.Directive.UpdateModel(mockModel, fallbacks, true)
		assertEquals(mockModel, cmd.model)
		assertEquals(fallbacks, cmd.fallbackModels)
		assertEquals(true, cmd.thinking)
	}
	
	@Test
	fun `UpdateModel with thinking false`() {
		val cmd = AgentCommand.Directive.UpdateModel(mockModel, thinking = false)
		assertEquals(false, cmd.thinking)
	}
	
	@Test
	fun `all Directive variants are distinct`() {
		val directives = listOf(
			AgentCommand.Directive.Stop,
			AgentCommand.Directive.Pause,
			AgentCommand.Directive.Resume,
			AgentCommand.Directive.Cancel,
			AgentCommand.Directive.Retry,
			AgentCommand.Directive.Compact,
			AgentCommand.Directive.UpdateModel(mockModel),
		)
		assertEquals(directives.size, directives.distinct().size)
	}
	
	@Test
	fun `Directive is AgentCommand`() {
		assertIs<AgentCommand>(AgentCommand.Directive.Stop)
		assertIs<AgentCommand>(AgentCommand.Directive.UpdateModel(mockModel))
	}
	
	// endregion
	
	// region Message
	
	@Test
	fun `SendMessage holds content string`() {
		val msg = AgentCommand.Message.SendMessage("hello world")
		assertEquals("hello world", msg.content)
		assertNull(msg.images)
	}
	
	@Test
	fun `SendMessage with images`() {
		val img = Base64("aaaa")
		val msg = AgentCommand.Message.SendMessage("hello", listOf(img))
		assertEquals("hello", msg.content)
		assertEquals(listOf(img), msg.images)
	}
	
	@Test
	fun `SendMessage with empty content`() {
		val msg = AgentCommand.Message.SendMessage("")
		assertEquals("", msg.content)
	}
	
	@Test
	fun `ApproveToolCall default approved is true`() {
		val approval = AgentCommand.Message.ApproveToolCall.Approve("call-1")
		assertEquals("call-1", approval.callId)
		assertNull(approval.reason)
		assertTrue(approval.approved)
	}
	
	@Test
	fun `ApproveToolCall with reason`() {
		val approval = AgentCommand.Message.ApproveToolCall.Approve("call-2", "looks safe")
		assertEquals("call-2", approval.callId)
		assertEquals("looks safe", approval.reason)
	}
	
	@Test
	fun `ApproveToolCall with rejected`() {
		val approval = AgentCommand.Message.ApproveToolCall.Approve("call-3", approved = false)
		assertEquals("call-3", approval.callId)
		assertFalse(approval.approved)
	}
	
	@Test
	fun `ApproveToolCall wraps list of approvals`() {
		val approvals = listOf(
			AgentCommand.Message.ApproveToolCall.Approve("c1"),
			AgentCommand.Message.ApproveToolCall.Approve("c2", approved = false),
		)
		val msg = AgentCommand.Message.ApproveToolCall(approvals)
		assertEquals(2, msg.approvals.size)
		assertEquals("c1", msg.approvals[0].callId)
		assertEquals("c2", msg.approvals[1].callId)
	}
	
	@Test
	fun `ApproveToolCall empty approvals`() {
		val msg = AgentCommand.Message.ApproveToolCall(emptyList())
		assertTrue(msg.approvals.isEmpty())
	}
	
	@Test
	fun `Message is AgentCommand`() {
		assertIs<AgentCommand>(AgentCommand.Message.SendMessage("hi"))
		assertIs<AgentCommand>(AgentCommand.Message.ApproveToolCall(emptyList()))
	}
	
	// endregion
}
