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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.core.TestServices
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.*

class EnvStoreTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private object TestEnvStore : EnvStore()
	
	private val secretMap: MutableMap<UUID, String> get() = TestServices.secretMap
	private val removedSecrets: MutableList<UUID> get() = TestServices.removedSecrets
	
	private val entries = mutableMapOf<KClass<*>, JsonElement?>()
	
	private fun entryFor(kClass: KClass<*>): JsonStore = mockk<JsonStore>().also {
		every { it.get() } answers { entries[kClass] }
		every { it.set(any()) } answers { entries[kClass] = firstArg<JsonElement>() }
	}
	
	@BeforeTest
	fun setUp() {
		entries.clear()
		secretMap.clear()
		removedSecrets.clear()
		every { TestServices.jsonStore.namespace(any()) } answers { entryFor(firstArg<KClass<*>>()) }
	}
	
	@Test
	fun `put and get secret round trip`() = runTest {
		TestEnvStore.setEnv("MY_KEY", "my value")
		
		assertEquals("my value", TestEnvStore.getEnv("MY_KEY"))
	}
	
	@Test
	fun `overwriting secret removes old secret reference`() = runTest {
		TestEnvStore.setEnv("MY_KEY", "first")
		val firstUuid = secretMap.entries.single { it.value == "first" }.key
		
		TestEnvStore.setEnv("MY_KEY", "second")
		
		assertEquals("second", TestEnvStore.getEnv("MY_KEY"))
		assertTrue(firstUuid in removedSecrets)
	}
	
	@Test
	fun `removeSecret removes mapping and secret`() = runTest {
		TestEnvStore.setEnv("MY_KEY", "value")
		val uuid = secretMap.entries.single().key
		
		assertTrue(TestEnvStore.removeEnv("MY_KEY"))
		assertNull(TestEnvStore.getEnv("MY_KEY"))
		assertTrue(uuid in removedSecrets)
	}
	
	@Test
	fun `removeSecret missing name returns false`() = runTest {
		assertFalse(TestEnvStore.removeEnv("MISSING"))
	}
	
	@Test
	fun `getSecret missing name returns null`() = runTest {
		assertNull(TestEnvStore.getEnv("MISSING"))
	}
	
	@Test
	fun `listSecrets returns stored names`() = runTest {
		TestEnvStore.setEnv("A", "1")
		TestEnvStore.setEnv("B", "2")
		
		assertEquals(setOf("A", "B"), TestEnvStore.listEnv().toSet())
	}
	
	@Test
	fun `secret retrieval failure returns null`() = runTest {
		TestEnvStore.setEnv("A", "1")
		// 删除底层 secret 使 get 抛异常
		val uuid = secretMap.entries.single().key
		secretMap.remove(uuid)
		
		assertNull(TestEnvStore.getEnv("A"))
	}
}
