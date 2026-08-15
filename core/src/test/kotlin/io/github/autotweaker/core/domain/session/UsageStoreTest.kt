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

import io.github.autotweaker.api.storage.JsonStore
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class UsageStoreTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private fun assistant(id: UUID = UUID.randomUUID(), usage: Usage? = Usage(100, 50, 50)) =
		AgentMessage.Assistant(
			id = id,
			timestamp = Clock.System.now(),
			reasoning = null,
			content = "reply",
			model = UUID.randomUUID(),
			usage = usage,
		)
	
	private fun compact(id: UUID = UUID.randomUUID()) = AgentMessage.Compact(
		id = id,
		timestamp = Clock.System.now(),
		content = "summary",
		usage = Usage(10, 5, 5),
	)
	
	private fun usageRecord(id: UUID = UUID.randomUUID()) = AgentMessage.UsageRecord(
		id = id,
		timestamp = Clock.System.now(),
		usage = Usage(7, 3, 4),
	)
	
	private fun user(id: UUID = UUID.randomUUID()) = AgentMessage.User(
		id = id,
		timestamp = Clock.System.now(),
		content = MessageContent(content = "question"),
	)
	
	private fun entryFor(): JsonStore = mockk<JsonStore>().also {
		every { it.get() } answers { null }
		every { it.set(any()) } answers { }
	}
	
	@BeforeTest
	fun setUp() {
		mockkObject(JsonStoreImpl)
		every { JsonStoreImpl.namespace(any()) } answers { entryFor() }
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStoreImpl)
	}
	
	@Test
	fun `collect records assistant usage`() = runTest {
		val id = UUID.randomUUID()
		
		UsageStore.collect(listOf(assistant(id)))
		
		assertEquals(Usage(100, 50, 50), UsageStore.getAll()[id])
	}
	
	@Test
	fun `collect records compact usage`() = runTest {
		val compactMsg = compact()
		
		UsageStore.collect(listOf(compactMsg))
		
		assertEquals(Usage(10, 5, 5), UsageStore.getAll()[compactMsg.id])
	}
	
	@Test
	fun `collect records usage record messages`() = runTest {
		val record = usageRecord()
		
		UsageStore.collect(listOf(record))
		
		assertEquals(Usage(7, 3, 4), UsageStore.getAll()[record.id])
	}
	
	@Test
	fun `collect ignores other message types`() = runTest {
		val before = UsageStore.getAll().size
		
		UsageStore.collect(listOf(user(), agentTool()))
		
		assertEquals(before, UsageStore.getAll().size)
	}
	
	@Test
	fun `duplicate message id uses latest usage`() = runTest {
		val id = UUID.randomUUID()
		
		UsageStore.collect(listOf(assistant(id)))
		UsageStore.collect(listOf(assistant(id, usage = Usage(999, 1, 1))))
		
		val usage = UsageStore.getAll()[id]
		assertNotNull(usage)
		assertEquals(Usage(999, 1, 1), usage)
	}
	
	@Test
	fun `getAll for unknown id returns null`() = runTest {
		assertNull(UsageStore.getAll()[UUID.randomUUID()])
	}
	
	private fun agentTool() = AgentMessage.Tool.Call(
		id = UUID.randomUUID(),
		timestamp = Instant.fromEpochMilliseconds(0),
		callId = "c1",
		callName = "bash-run",
		arguments = "{}",
		reason = null,
		validatedToolName = null,
		validatedArgs = null,
		resolvedRequest = null,
		presentation = null,
	)
}
