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

import io.github.autotweaker.core.agent.tool.Tools
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MutableAgentStateTest {
	
	@Test
	fun `default state has null pendingApproval and processedTools`() {
		val state = MutableAgentState()
		assertNull(state.pendingApproval)
		assertNull(state.processedTools)
	}
	
	@Test
	fun `can set pendingApproval`() {
		val state = MutableAgentState()
		val approval: Tools.ToolCallResolveResult.NeedsApproval = mockk(relaxed = true)
		state.pendingApproval = listOf(approval)
		
		assertNotNull(state.pendingApproval)
		assertEquals(1, state.pendingApproval!!.size)
	}
	
	@Test
	fun `can set processedTools`() {
		val state = MutableAgentState()
		val toolMsg: AgentContext.Message.Tool = mockk(relaxed = true)
		state.processedTools = listOf(toolMsg)
		
		assertNotNull(state.processedTools)
		assertEquals(1, state.processedTools!!.size)
	}
	
	@Test
	fun `can set both fields`() {
		val state = MutableAgentState()
		val approval: Tools.ToolCallResolveResult.NeedsApproval = mockk(relaxed = true)
		val toolMsg: AgentContext.Message.Tool = mockk(relaxed = true)
		
		state.pendingApproval = listOf(approval)
		state.processedTools = listOf(toolMsg)
		
		assertNotNull(state.pendingApproval)
		assertNotNull(state.processedTools)
	}
	
	@Test
	fun `can clear fields back to null`() {
		val state = MutableAgentState()
		state.pendingApproval = listOf(mockk(relaxed = true))
		state.pendingApproval = null
		
		assertNull(state.pendingApproval)
	}
}
