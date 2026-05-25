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

package io.github.autotweaker.core.data.json

import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persistence.store.h2.H2DatabaseStore
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class JsonStoreImplTest {
	
	@BeforeTest
	fun setUp() {
		mockkObject(H2DatabaseStore)
		every { H2DatabaseStore.connect(any()) } answers {
			Database.connect("jdbc:h2:mem:js;DB_CLOSE_DELAY=-1", "org.h2.Driver")
		}
		val field = JsonStoreImpl::class.java.getDeclaredField("initialized")
		field.isAccessible = true
		field.setBoolean(JsonStoreImpl, false)
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(H2DatabaseStore)
	}
	
	@Test
	fun `init then get returns null`() {
		JsonStoreImpl.init()
		assertNull(JsonStoreImpl.namespace(String::class).get())
	}
	
	@Test
	fun `namespace and set then get`() {
		JsonStoreImpl.init()
		val entry = JsonStoreImpl.namespace(Int::class)
		val data = buildJsonObject { put("k", JsonPrimitive("v")) }
		entry.set(data)
		assertNotNull(entry.get())
	}
	
	@Test
	fun `get handles corrupted JSON`() {
		JsonStoreImpl.init()
		transaction {
			JsonStoreTable.insert {
				it[JsonStoreTable.namespace] = Boolean::class.java.name
				it[JsonStoreTable.content] = "bad json"
			}
		}
		assertNull(JsonStoreImpl.namespace(Boolean::class).get())
	}
}
