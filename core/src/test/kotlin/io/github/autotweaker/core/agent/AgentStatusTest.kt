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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentStatusTest {
	
	@Test
	fun `all seven status values exist`() {
		assertEquals(7, AgentStatus.entries.size)
	}
	
	@Test
	fun `FREE is the first status`() {
		assertEquals(AgentStatus.FREE, AgentStatus.valueOf("FREE"))
	}
	
	@Test
	fun `all values can be resolved by name`() {
		for (status in AgentStatus.entries) {
			assertNotNull(AgentStatus.valueOf(status.name))
		}
	}
	
	@Test
	fun `status values are distinct`() {
		val names = AgentStatus.entries.map { it.name }.toSet()
		assertEquals(AgentStatus.entries.size, names.size)
	}
	
	@Test
	fun `FREE ordinal is 0`() {
		assertEquals(0, AgentStatus.FREE.ordinal)
	}
	
	@Test
	fun `PROCESSING exists`() {
		assertEquals(AgentStatus.PROCESSING, AgentStatus.valueOf("PROCESSING"))
	}
	
	@Test
	fun `TOOL_CALLING exists`() {
		assertEquals(AgentStatus.TOOL_CALLING, AgentStatus.valueOf("TOOL_CALLING"))
	}
	
	@Test
	fun `WAITING exists`() {
		assertEquals(AgentStatus.WAITING, AgentStatus.valueOf("WAITING"))
	}
	
	@Test
	fun `RETRYING exists`() {
		assertEquals(AgentStatus.RETRYING, AgentStatus.valueOf("RETRYING"))
	}
	
	@Test
	fun `PAUSED exists`() {
		assertEquals(AgentStatus.PAUSED, AgentStatus.valueOf("PAUSED"))
	}
	
	@Test
	fun `ERROR exists`() {
		assertEquals(AgentStatus.ERROR, AgentStatus.valueOf("ERROR"))
	}
	
	@Test
	fun `all status names are uppercase`() {
		for (status in AgentStatus.entries) {
			assertTrue(status.name.all { it.isUpperCase() || it == '_' })
		}
	}
}
