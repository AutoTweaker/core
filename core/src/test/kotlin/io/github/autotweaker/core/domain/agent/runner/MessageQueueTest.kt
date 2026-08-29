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

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.chat.merge
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class MessageQueueTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private fun queue() = MessageQueue(UUID.randomUUID())
	
	private fun text(content: String) = MessageContent(content = content.toContentPart())
	
	private fun injection(tag: String, content: String) =
		ContextInjection(tag = tag, content = content)
	
	// region merge
	
	@Test
	fun `merge empty map returns null`() = runTest {
		assertNull(queue().merge(emptyMap()))
	}
	
	@Test
	fun `merge single text`() = runTest {
		val result = queue().merge(mapOf(UUID.randomUUID() to text("hello")))
		
		assertNotNull(result)
		assertEquals("hello\n", result.content.content?.merge())
	}
	
	@Test
	fun `merge joins multiple texts with separator`() = runTest {
		val result = queue().merge(
			mapOf(
				UUID.randomUUID() to text("first"),
				UUID.randomUUID() to text("second"),
			)
		)
		
		assertNotNull(result)
		assertEquals("first\nsecond\n", result.content.content?.merge())
	}
	
	@Test
	fun `merge filters blank texts`() = runTest {
		val result = queue().merge(
			mapOf(
				UUID.randomUUID() to text(""),
				UUID.randomUUID() to text("   "),
				UUID.randomUUID() to text("real"),
			)
		)
		
		assertNotNull(result)
		assertEquals("\n   \nreal\n", result.content.content?.merge())
	}
	
	@Test
	fun `merge all blank returns null`() = runTest {
		val result = queue().merge(mapOf(UUID.randomUUID() to text("   ")))
		
		// 空白文本仍保留为 ContentPart.Text，不再被过滤
		assertNotNull(result)
		assertEquals("   \n", result.content.content?.merge())
	}
	
	@Test
	fun `merge combines injections`() = runTest {
		val result = queue().merge(
			mapOf(
				UUID.randomUUID() to MessageContent(injections = listOf(injection("a", "1"))),
				UUID.randomUUID() to MessageContent(injections = listOf(injection("b", "2"))),
			)
		)
		
		assertNotNull(result)
		assertEquals(2, result.content.injections?.size)
		assertEquals(listOf("a", "b"), result.content.injections?.map { it.tag })
	}
	
	@Test
	fun `merge combines images`() = runTest {
		val img1 = Sha256(ByteArray(32) { 1 })
		val img2 = Sha256(ByteArray(32) { 2 })
		val result = queue().merge(
			mapOf(
				UUID.randomUUID() to MessageContent(content = listOf(ContentPart.Image("image/png", img1))),
				UUID.randomUUID() to MessageContent(content = listOf(ContentPart.Image("image/png", img2))),
			)
		)
		
		assertNotNull(result)
		assertEquals(
			listOf(img1, img2),
			result.content.content?.mapNotNull { (it as? ContentPart.Image)?.data },
		)
	}
	
	// endregion
	
	// region send/drain/receive
	
	@Test
	fun `drain empty queue returns null`() = runTest {
		assertNull(queue().drain())
	}
	
	@Test
	fun `send then drain returns merged message`() = runTest {
		val q = queue()
		q.send("hello")
		
		val message = q.drain()
		
		assertNotNull(message)
		assertEquals("hello\n", message.content.content?.merge())
	}
	
	@Test
	fun `send await returns merged message id`() = runTest {
		val q = queue()
		val delivery = q.send("hello")
		
		val message = q.drain()
		assertNotNull(message)
		assertEquals(message.id, delivery.await()?.first)
	}
	
	@Test
	fun `receive merges backlog of multiple sends`() = runTest {
		val q = queue()
		val received = async { q.receive() }
		
		q.send("first")
		q.send("second")
		
		val message = received.await()
		assertEquals("first\nsecond\n", message.content.content?.merge())
	}
	
	@Test
	fun `cancelled delivery is filtered from merge`() = runTest {
		val q = queue()
		val delivery = q.send("to be cancelled")
		delivery.cancel()
		
		assertNull(q.drain())
	}
	
	@Test
	fun `cancelled delivery await throws`() = runTest {
		val q = queue()
		val delivery = q.send("x")
		delivery.cancel()
		
		assertFailsWith<CancellationException> { delivery.await() }
	}
	
	@Test
	fun `blank message resolves delivery with null`() = runTest {
		val q = queue()
		val delivery = q.send("   ")
		
		// 空白文本仍保留为 ContentPart.Text，消息不再被丢弃
		val message = q.drain()
		assertNotNull(message)
		assertEquals(message.id, delivery.await()?.first)
	}
	
	@Test
	fun `send list returns one delivery per message`() = runTest {
		val q = queue()
		val deliveries = q.send(listOf("a", "b"))
		assertEquals(2, deliveries.size)
		
		val message = q.drain()
		assertNotNull(message)
		assertEquals("a\nb\n", message.content.content?.merge())
		deliveries.forEach { assertEquals(message.id, it.await()?.first) }
	}
	
	// endregion
}
