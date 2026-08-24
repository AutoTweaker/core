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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private fun session() = Session(
		data = SessionData(
			id = UUID.randomUUID(),
			title = "original title",
			overview = null,
			workspaceId = UUID.randomUUID(),
			agentIndex = AgentIndex.new(),
		),
		sessionRepo = mockk<SessionRepository>(),
		usageRepo = mockk<UsageRepository>(),
		resolveModel = { mockk<Model>() },
		workspace = Path.of("/tmp"),
	)
	
	@Test
	fun `updateTitle updates session data`() = runTest {
		val s = session()
		
		s.updateTitle { "new title" }
		
		assertEquals("new title", s.data.value.title)
	}
	
	@Test
	fun `data exposes initial session data`() = runTest {
		val data = session().data.value
		
		assertEquals("original title", data.title)
		assertNotNull(data.id)
		assertNotNull(data.workspaceId)
	}
	
	@Test
	fun `agents is empty before init`() = runTest {
		val s = session()
		
		assertTrue(s.agents.isEmpty())
	}
	
	@Test
	fun `shutdown without bridges is safe`() = runTest {
		val s = session()
		
		s.shutdown()
		
		assertTrue(s.agents.isEmpty())
	}
}
