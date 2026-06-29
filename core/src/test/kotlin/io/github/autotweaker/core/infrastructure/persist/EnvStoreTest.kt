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

package io.github.autotweaker.core.infrastructure.persist

import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.test.*

class TestEnvStore : EnvStore()

class EnvStoreTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private lateinit var store: TestEnvStore
	private var storedJson: JsonElement? = null
	
	@BeforeTest
	fun setUp() {
		storedJson = null
		mockkObject(JsonStoreImpl)
		val mockEntry = mockk<JsonStore>()
		every { mockEntry.get() } answers { storedJson }
		every { mockEntry.set(any()) } answers { storedJson = firstArg<JsonElement>() }
		every { JsonStoreImpl.namespace(any()) } returns mockEntry
		
		val secretStore = object : SecretStore {
			private val map = mutableMapOf<UUID, String>()
			override suspend fun set(secret: String, id: UUID): UUID = id.also { map[it] = secret }
			override suspend fun get(id: UUID): String = map[id]!!
			override suspend fun list(): List<UUID> = map.keys.toList()
			override suspend fun remove(id: UUID): Boolean = map.remove(id) != null
			override fun requireUnlocked() {}
		}
		
		EnvStore.init(secretStore)
		store = TestEnvStore()
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStoreImpl)
	}
	
	@Test
	fun `getEnv returns null for non-existent id`() = runBlocking {
		assertNull(store.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv and getEnv roundtrip`() = runBlocking {
		store.setEnv("MY_KEY", "my_value")
		assertEquals("my_value", store.getEnv("MY_KEY"))
	}
	
	@Test
	fun `setEnv overwrites existing value`() = runBlocking {
		store.setEnv("KEY", "old")
		store.setEnv("KEY", "new")
		assertEquals("new", store.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv deletes existing entry`() = runBlocking {
		store.setEnv("KEY", "value")
		store.removeEnv("KEY")
		assertNull(store.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv is no-op for non-existent entry`() = runBlocking {
		assertNull(store.getEnv("NONEXISTENT"))
		store.removeEnv("NONEXISTENT")
		assertNull(store.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv with special characters`() = runBlocking {
		store.setEnv("KEY", "value with spaces and \"quotes\"")
		assertEquals("value with spaces and \"quotes\"", store.getEnv("KEY"))
	}
	
	@Test
	fun `multiple env entries coexist`() = runBlocking {
		store.setEnv("A", "1")
		store.setEnv("B", "2")
		store.setEnv("C", "3")
		assertEquals("1", store.getEnv("A"))
		assertEquals("2", store.getEnv("B"))
		assertEquals("3", store.getEnv("C"))
	}
}
