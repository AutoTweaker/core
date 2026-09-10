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

import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.session.SessionCursor
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionSort
import io.github.autotweaker.api.types.session.toCursor
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SessionRepositoryQueryTest {
	private val dbUrl = "jdbc:h2:mem:session_query_${counter.getAndIncrement()};DB_CLOSE_DELAY=-1"
	
	companion object {
		private val counter = AtomicInteger(0)
	}
	
	private lateinit var repo: SessionRepositoryImpl
	
	private val workspaceA = UUID.randomUUID()
	private val workspaceB = UUID.randomUUID()
	private val baseTime = Instant.parse("2026-09-01T00:00:00Z")
	
	@BeforeTest
	fun setUp() {
		val databaseStore = mockk<DatabaseStore>()
		every { databaseStore.connect(any()) } answers { Database.connect(dbUrl, "org.h2.Driver") }
		repo = SessionRepositoryImpl(databaseStore)
	}
	
	private fun session(
		workspaceId: UUID = workspaceA,
		creationTime: Instant = baseTime,
		lastAccessTime: Instant = baseTime,
	) = SessionData(
		id = UUID.randomUUID(),
		title = null,
		overview = null,
		workspaceId = workspaceId,
		creationTime = creationTime,
		lastAccessTime = lastAccessTime,
		agentIndex = AgentIndex.new(),
	)
	
	@Test
	fun `session table indexes cover paginated queries`() {
		val indexed = SessionDataTable.indices.mapTo(mutableSetOf()) { index ->
			index.columns.map { it.name }
		}
		
		assertEquals(
			setOf(
				listOf("workspace_id", "last_access_time", "id"),
				listOf("workspace_id", "creation_time", "id"),
				listOf("last_access_time", "id"),
				listOf("creation_time", "id"),
			),
			indexed,
		)
	}
	
	@Test
	fun `querySessions orders across workspaces when no workspace filter`() = runBlocking {
		val inA = session(workspaceId = workspaceA, lastAccessTime = baseTime.plus(1.hours))
		val inB = session(workspaceId = workspaceB, lastAccessTime = baseTime.plus(2.hours))
		repo.saveSessions(listOf(inA, inB))
		
		val result = repo.loadSessions(null, SessionSort.LAST_ACCESS_TIME, 10, null)
		
		assertEquals(listOf(inB.id, inA.id), result.map { it.id })
	}
	
	@Test
	fun `querySessions orders by last access time descending`() = runBlocking {
		val oldest = session(lastAccessTime = baseTime)
		val middle = session(lastAccessTime = baseTime.plus(1.hours))
		val newest = session(lastAccessTime = baseTime.plus(2.hours))
		repo.saveSessions(listOf(oldest, newest, middle))
		
		val result = repo.loadSessions(null, SessionSort.LAST_ACCESS_TIME, 10, null)
		
		assertEquals(listOf(newest.id, middle.id, oldest.id), result.map { it.id })
	}
	
	@Test
	fun `querySessions orders by creation time descending`() = runBlocking {
		// 两个时间字段的先后顺序相反，确保排序确实落在 creation_time 上
		val older = session(creationTime = baseTime, lastAccessTime = baseTime.plus(9.hours))
		val newer = session(creationTime = baseTime.plus(2.hours), lastAccessTime = baseTime)
		repo.saveSessions(listOf(older, newer))
		
		val result = repo.loadSessions(null, SessionSort.CREATION_TIME, 10, null)
		
		assertEquals(listOf(newer.id, older.id), result.map { it.id })
	}
	
	@Test
	fun `querySessions filters by workspace`() = runBlocking {
		val inA = session(workspaceId = workspaceA)
		val inB = session(workspaceId = workspaceB)
		repo.saveSessions(listOf(inA, inB))
		
		val resultA = repo.loadSessions(workspaceA, SessionSort.LAST_ACCESS_TIME, 10, null)
		val resultB = repo.loadSessions(workspaceB, SessionSort.LAST_ACCESS_TIME, 10, null)
		
		assertEquals(listOf(inA.id), resultA.map { it.id })
		assertEquals(listOf(inB.id), resultB.map { it.id })
	}
	
	@Test
	fun `querySessions respects limit`() = runBlocking {
		val sessions = (0 until 5).map { session(lastAccessTime = baseTime.plus(it.hours)) }
		repo.saveSessions(sessions)
		
		val result = repo.loadSessions(null, SessionSort.LAST_ACCESS_TIME, 2, null)
		
		assertEquals(listOf(sessions[4].id, sessions[3].id), result.map { it.id })
	}
	
	@Test
	fun `querySessions pages through cursor without duplicates or gaps`() = runBlocking {
		val sessions = (0 until 7).map { session(lastAccessTime = baseTime.plus(it.hours)) }
		repo.saveSessions(sessions)
		val expected = sessions.sortedByDescending { it.lastAccessTime }.map { it.id }
		
		val collected = mutableListOf<UUID>()
		var cursor: SessionCursor? = null
		while (collected.size < sessions.size) {
			val page = repo.loadSessions(null, SessionSort.LAST_ACCESS_TIME, 2, cursor)
			check(page.isNotEmpty()) { "游标分页在取完 $expected 之前就返回了空页" }
			collected += page.map { it.id }
			cursor = page.last().toCursor(SessionSort.LAST_ACCESS_TIME)
		}
		
		assertEquals(expected, collected)
	}
	
	@Test
	fun `querySessions pages through sessions sharing a timestamp`() = runBlocking {
		val sessions = (0 until 3).map { session(lastAccessTime = baseTime) }
		repo.saveSessions(sessions)
		
		val first = repo.loadSessions(null, SessionSort.LAST_ACCESS_TIME, 2, null)
		val second = repo.loadSessions(
			null, SessionSort.LAST_ACCESS_TIME, 2,
			first.last().toCursor(SessionSort.LAST_ACCESS_TIME),
		)
		
		assertEquals(2, first.size)
		assertEquals(1, second.size)
		assertEquals(sessions.map { it.id }.toSet(), (first + second).map { it.id }.toSet())
	}
	
	@Test
	fun `querySessions keeps workspace filter across cursor pages`() = runBlocking {
		val inA = (0 until 4).map { session(workspaceId = workspaceA, lastAccessTime = baseTime.plus(it.hours)) }
		val inB = (0 until 4).map { session(workspaceId = workspaceB, lastAccessTime = baseTime.plus(it.hours)) }
		repo.saveSessions(inA + inB)
		
		val first = repo.loadSessions(workspaceA, SessionSort.LAST_ACCESS_TIME, 2, null)
		val second = repo.loadSessions(
			workspaceA, SessionSort.LAST_ACCESS_TIME, 2,
			first.last().toCursor(SessionSort.LAST_ACCESS_TIME),
		)
		
		assertEquals(
			inA.sortedByDescending { it.lastAccessTime }.map { it.id },
			(first + second).map { it.id },
		)
	}
}
