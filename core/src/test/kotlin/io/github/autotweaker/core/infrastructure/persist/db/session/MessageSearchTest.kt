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

import io.github.autotweaker.api.types.agent.AgentMessageType
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MessageSearchTest {
	private val baseTime = Instant.parse("2026-09-01T00:00:00Z")
	
	@Test
	fun `search hits matching english words`() = runBlocking {
		val fox = upsert("quick brown fox")
		val dog = upsert("lazy dog")
		
		assertEquals(setOf(fox), search("fox"))
		assertEquals(setOf(dog), search("dog"))
		assertTrue(search("elephant").isEmpty())
	}
	
	@Test
	fun `search hits whole chinese sentence`() = runBlocking {
		val message = upsert("今天天气很好")
		
		assertEquals(setOf(message), search("今天天气很好"))
	}
	
	@Test
	fun `search hits chinese substring of two characters`() = runBlocking {
		val message = upsert("湖边柳树成荫")
		
		assertEquals(setOf(message), search("柳树"))
	}
	
	@Test
	fun `search misses chinese single character`() = runBlocking {
		upsert("春风吹拂大地")
		
		assertTrue(search("天").isEmpty())
	}
	
	@Test
	fun `search hits chinese words separated by spaces`() = runBlocking {
		val message = upsert("苹果 香蕉 西瓜")
		
		assertEquals(setOf(message), search("香蕉"))
	}
	
	@Test
	fun `search splits words on ascii punctuation`() = runBlocking {
		val message = upsert("hello,world how-are you?")
		
		assertEquals(setOf(message), search("world"))
		assertEquals(setOf(message), search("hello"))
	}
	
	@Test
	fun `search splits on chinese punctuation`() = runBlocking {
		val message = upsert("你好，世界。今天如何？")
		
		assertEquals(setOf(message), search("世界"))
	}
	
	@Test
	fun `search requires all query words`() = runBlocking {
		val both = upsert("red apple green")
		val partial = upsert("red only")
		
		assertEquals(setOf(both), search("red green"))
		assertEquals(setOf(both, partial), search("red"))
	}
	
	@Test
	fun `search filters by type`() = runBlocking {
		val user = UUID.randomUUID()
		val assistant = UUID.randomUUID()
		MessageSearch.upsert(user, AgentMessageType.USER, baseTime, "type shared phrase")
		MessageSearch.upsert(assistant, AgentMessageType.ASSISTANT, baseTime, "type shared phrase")
		
		assertEquals(setOf(user), typeSearch("phrase", AgentMessageType.USER))
		assertEquals(setOf(assistant), typeSearch("phrase", AgentMessageType.ASSISTANT))
	}
	
	@Test
	fun `search filters by timestamp range`() = runBlocking {
		val earlier = upsert("time marker", baseTime)
		val later = upsert("time marker", baseTime + 1.seconds)
		
		val fromHits = rangeSearch("marker", baseTime + 1.seconds, null)
		val toHits = rangeSearch("marker", null, baseTime)
		assertEquals(setOf(later), fromHits)
		assertEquals(setOf(earlier), toHits)
	}
	
	@Test
	fun `delete removes message from search results`() = runBlocking {
		val message = upsert("obsolete content")
		
		assertEquals(setOf(message), search("obsolete"))
		
		MessageSearch.delete(setOf(message))
		
		assertTrue(search("obsolete").isEmpty())
	}
	
	private suspend fun upsert(text: String, time: Instant = baseTime): UUID {
		val id = UUID.randomUUID()
		MessageSearch.upsert(id, AgentMessageType.USER, time, text)
		return id
	}
	
	private suspend fun search(query: String): Set<UUID> =
		MessageSearch.search(query, null, null, null)
	
	private suspend fun typeSearch(query: String, type: AgentMessageType): Set<UUID> =
		MessageSearch.search(query, type, null, null)
	
	private suspend fun rangeSearch(query: String, from: Instant?, to: Instant?): Set<UUID> =
		MessageSearch.search(query, null, from, to)
}
