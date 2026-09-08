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

package io.github.autotweaker.core.infrastructure.persist.db.session

import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SessionRepositorySearchTest {
	private val dbUrl = "jdbc:h2:mem:session_search_${counter.getAndIncrement()};DB_CLOSE_DELAY=-1"
	
	companion object {
		private val counter = AtomicInteger(0)
	}
	
	private lateinit var repo: SessionRepositoryImpl
	
	private val sessionId = UUID.randomUUID()
	private val agentId = UUID.randomUUID()
	private val baseTime = Instant.parse("2026-09-01T00:00:00Z")
	
	@BeforeTest
	fun setUp() = runBlocking {
		val databaseStore = mockk<DatabaseStore>()
		every { databaseStore.connect(any()) } answers { Database.connect(dbUrl, "org.h2.Driver") }
		mockkObject(MessageSearch)
		coEvery { MessageSearch.upsert(any(), any(), any(), any()) } returns Unit
		coEvery { MessageSearch.delete(any()) } returns Unit
		repo = SessionRepositoryImpl(databaseStore)
		
		// saveMessages 会向 message_ownership 插入外键行，需要先存在 session 与 agent
		repo.saveSessions(
			listOf(
				SessionData(
					id = sessionId,
					title = null,
					overview = null,
					workspaceId = UUID.randomUUID(),
					creationTime = baseTime,
					lastAccessTime = baseTime,
					agentIndex = AgentIndex.new(),
				)
			)
		)
		repo.saveAgent(
			AgentData(
				id = agentId,
				name = "main".toKebab(),
				sessionId = sessionId,
				creationTime = baseTime,
				lastAccessTime = baseTime,
				model = ModelConfig(
					model = UUID.randomUUID(),
					summarize = UUID.randomUUID(),
					compact = UUID.randomUUID(),
					fallback = emptyList(),
					reasoning = null,
				),
				context = AgentContext.emptyContext("system prompt"),
				activeTools = emptySet(),
			)
		)
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(MessageSearch)
	}
	
	private fun userMessage(id: UUID, text: String, time: Instant) = AgentMessage.User(
		id = id,
		timestamp = time,
		origin = setOf(agentId),
		content = MessageContent(content = text.toContentPart()),
	)
	
	private fun assistantMessage(id: UUID, text: String, time: Instant) = AgentMessage.Assistant(
		id = id,
		timestamp = time,
		origin = setOf(agentId),
		reasoning = null,
		content = text,
		model = UUID.randomUUID(),
		usage = null,
	)
	
	@Test
	fun `saveMessages mirrors messages into search index`() = runBlocking {
		val message = userMessage(UUID.randomUUID(), "hello world", baseTime)
		repo.saveMessages(listOf(message))
		
		coVerify(exactly = 1) {
			MessageSearch.upsert(message.id, AgentMessageType.USER, baseTime, "hello world")
		}
	}
	
	@Test
	fun `saveMessages mirrors usage records without search text`() = runBlocking {
		val message = AgentMessage.UsageRecord(
			id = UUID.randomUUID(),
			timestamp = baseTime,
			origin = setOf(agentId),
			model = UUID.randomUUID(),
			usage = Usage.ZERO,
		)
		repo.saveMessages(listOf(message))
		
		coVerify(exactly = 1) {
			MessageSearch.upsert(message.id, AgentMessageType.USAGE_RECORD, baseTime, null)
		}
	}
	
	@Test
	fun `searchMessages delegates to search index`() = runBlocking {
		val hit = UUID.randomUUID()
		coEvery { MessageSearch.search("fox", null, null, null) } returns setOf(hit)
		
		assertEquals(setOf(hit), repo.searchMessages("fox", null, null, null))
	}
	
	@Test
	fun `searchMessages passes type and timestamp filters through`() = runBlocking {
		val hit = UUID.randomUUID()
		val from = baseTime
		val to = baseTime.plus(1.seconds)
		coEvery { MessageSearch.search("needle", AgentMessageType.USER, from, to) } returns setOf(hit)
		
		assertEquals(
			setOf(hit),
			repo.searchMessages("needle", AgentMessageType.USER, from, to)
		)
	}
	
	@Test
	fun `deleteSessions removes orphan messages from search index`() = runBlocking {
		val message = userMessage(UUID.randomUUID(), "stale content", baseTime)
		repo.saveMessages(listOf(message))
		
		repo.deleteSessions(setOf(sessionId))
		
		coVerify(exactly = 1) {
			MessageSearch.delete(setOf(message.id))
		}
	}
}
