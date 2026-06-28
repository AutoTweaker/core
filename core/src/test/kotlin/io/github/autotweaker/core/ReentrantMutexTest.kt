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

package io.github.autotweaker.core

import io.github.autotweaker.api.ReentrantMutex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class ReentrantMutexTest {
	
	private val lock = ReentrantMutex()
	private val otherLock = ReentrantMutex()
	
	@Test
	fun `serial access`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		val log = mutableListOf<String>()
		withTimeout(5000.milliseconds) {
			coroutineScope {
				launch {
					lock.withLock {
						log.add("A-enter")
						delay(50.milliseconds)
						log.add("A-exit")
					}
				}
				launch {
					delay(10.milliseconds)
					lock.withLock {
						log.add("B-enter")
						log.add("B-exit")
					}
				}
			}
		}
		assertEquals(listOf("A-enter", "A-exit", "B-enter", "B-exit"), log)
	}
	
	@Test
	fun `reentrant succeeds`() = runBlocking {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock { lock.withLock { "done" } }
		}
		assertEquals("done", result)
	}
	
	@Test
	fun `exception releases lock`() = runBlocking {
		TestServices.init()
		withTimeout(5000.milliseconds) {
			runCatching { lock.withLock { throw RuntimeException("fail") } }
			val result = lock.withLock { "recovered" }
			assertEquals("recovered", result)
		}
	}
	
	@Test
	fun `reentrant exception preserves lock`() = runBlocking {
		TestServices.init()
		withTimeout(5000.milliseconds) {
			runCatching {
				lock.withLock { lock.withLock { throw RuntimeException("inner") } }
			}
			val result = lock.withLock { "still-accessible" }
			assertEquals("still-accessible", result)
		}
	}
	
	@Test
	fun `deep reentry`() = runBlocking {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock {
				lock.withLock { lock.withLock { "deep" } }
			}
		}
		assertEquals("deep", result)
	}
	
	@Test
	fun `extreme 10-level reentry`() = runBlocking {
		TestServices.init()
		var depth = 0
		suspend fun go(n: Int): String = lock.withLock {
			depth++
			if (n <= 1) "bottom($depth)" else go(n - 1)
		}
		
		val result = withTimeout(5000.milliseconds) { go(10) }
		assertEquals("bottom(10)", result)
	}
	
	@Test
	fun `reentrant across withContext`() = runBlocking {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock { withContext(Dispatchers.Default) { lock.withLock { "cross-dispatcher" } } }
		}
		assertEquals("cross-dispatcher", result)
	}
	
	@Test
	fun `reentrant across withTimeout`() = runBlocking {
		TestServices.init()
		val result = withTimeout(10000.milliseconds) {
			lock.withLock { withTimeout(5000.milliseconds) { lock.withLock { "cross-timeout" } } }
		}
		assertEquals("cross-timeout", result)
	}
	
	@Test
	fun `reentrant across coroutineScope`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock {
				coroutineScope { lock.withLock { "cross-scope" } }
			}
		}
		assertEquals("cross-scope", result)
	}
	
	@Test
	fun `100 concurrent coroutines serialize`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		var counter = 0
		withTimeout(10000.milliseconds) {
			val jobs = List(100) {
				launch { lock.withLock { counter++ } }
			}
			jobs.joinAll()
		}
		assertEquals(100, counter)
	}
	
	@Test
	fun `different locks are independent`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		val log = mutableListOf<String>()
		withTimeout(5000.milliseconds) {
			launch {
				lock.withLock {
					log.add("lock-enter")
					otherLock.withLock {
						log.add("other-enter")
						delay(50.milliseconds)
						log.add("other-exit")
					}
					log.add("lock-exit")
				}
			}
			launch {
				delay(10.milliseconds)
				otherLock.withLock {
					log.add("B-other-enter")
					log.add("B-other-exit")
				}
			}
		}
		assertTrue(log.indexOf("lock-enter") < log.indexOf("other-enter"))
		assertTrue(log.indexOf("other-enter") < log.indexOf("other-exit"))
		assertTrue(log.indexOf("other-exit") < log.indexOf("lock-exit"))
		assertTrue(log.indexOf("B-other-enter") < log.indexOf("B-other-exit"))
	}
	
	@Test
	fun `A-B-A interleaved reentrancy`() = runBlocking {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock {
				otherLock.withLock {
					lock.withLock { "interleaved" }
				}
			}
		}
		assertEquals("interleaved", result)
	}
	
	@Test
	fun `cancellation while waiting releases nothing but does not corrupt`() = runBlocking {
		TestServices.init()
		withTimeout(5000.milliseconds) {
			lock.withLock {
				val job = launch(Dispatchers.Default) {
					lock.withLock { fail("should not acquire") }
				}
				delay(10.milliseconds)
				job.cancelAndJoin()
			}
			lock.withLock { /* lock still usable */ }
		}
	}
	
	@Test
	fun `cancellation during locked block releases lock`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		withTimeout(5000.milliseconds) {
			val locked = Job()
			val job = launch {
				lock.withLock {
					locked.complete()
					delay(Long.MAX_VALUE.milliseconds)
				}
			}
			locked.join()
			job.cancelAndJoin()
			lock.withLock { /* re-acquire after cancellation */ }
		}
	}
	
	@Test
	fun `reentrant cancellation does not corrupt outer lock`() = runBlocking {
		TestServices.init()
		withTimeout(5000.milliseconds) {
			lock.withLock {
				runCatching {
					lock.withLock { throw CancellationException("inner") }
				}
			}
			lock.withLock { /* outer released correctly */ }
		}
	}
	
	@Test
	fun `heavy concurrent contention with active reentry`() = runBlocking(Dispatchers.Default) {
		TestServices.init()
		val readyLatch = Job()
		var reentrantSuccess = false
		var externalCounter = 0
		
		withTimeout(10000.milliseconds) {
			val holderJob = launch {
				lock.withLock {
					readyLatch.complete()
					delay(50.milliseconds)
					lock.withLock { lock.withLock { reentrantSuccess = true } }
				}
			}
			
			readyLatch.join()
			
			val contenders = List(99) {
				launch { lock.withLock { externalCounter++ } }
			}
			
			holderJob.join()
			contenders.joinAll()
		}
		
		assertTrue(reentrantSuccess)
		assertEquals(99, externalCounter)
	}
	
	@Test
	fun `A-B-A-B chaotic interleaved reentry`() = runBlocking {
		TestServices.init()
		val result = withTimeout(5000.milliseconds) {
			lock.withLock {
				otherLock.withLock {
					lock.withLock {
						otherLock.withLock {
							lock.withLock { "chaotic-done" }
						}
					}
				}
			}
		}
		assertEquals("chaotic-done", result)
	}
	
	@Test
	fun `extreme flow reentrancy with thread switching`() = runBlocking {
		TestServices.init()
		val log = mutableListOf<String>()
		
		withTimeout(5000.milliseconds) {
			lock.withLock {
				log.add("outer-enter")
				
				val f = flow {
					lock.withLock { emit("item-1"); emit("item-2") }
				}.flowOn(Dispatchers.Default)
				
				f.collect { item: String ->
					lock.withLock { log.add("collect-$item") }
				}
				log.add("outer-exit")
			}
		}
		
		assertEquals(listOf("outer-enter", "collect-item-1", "collect-item-2", "outer-exit"), log)
	}
}
