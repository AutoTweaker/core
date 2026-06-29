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

import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonStoreImplTest {
	
	private val dbUrl = "jdbc:h2:mem:js_${counter.getAndIncrement()};DB_CLOSE_DELAY=-1"
	
	companion object {
		private val counter = atomic(0)
		
		init {
			TestServices.init()
		}
	}
	
	private lateinit var databaseStore: DatabaseStore
	
	@BeforeTest
	fun setUp() {
		databaseStore = mockk()
		every { databaseStore.connect(any()) } answers {
			Database.connect(dbUrl, "org.h2.Driver")
		}
	}
	
	@Test
	fun `init then get returns null`() {
		JsonStoreImpl.init(databaseStore)
		assertNull(JsonStoreImpl.namespace(String::class).get())
	}
	
	@Test
	fun `namespace and set then get`() {
		JsonStoreImpl.init(databaseStore)
		val entry = JsonStoreImpl.namespace(Int::class)
		val data = buildJsonObject { put("k", JsonPrimitive("v")) }
		entry.set(data)
		assertNotNull(entry.get())
	}
	
	@Test
	fun `get handles corrupted JSON`() {
		JsonStoreImpl.init(databaseStore)
		transaction {
			JsonStoreTable.insert {
				it[JsonStoreTable.namespace] = Boolean::class.java.name
				it[JsonStoreTable.content] = "bad json"
			}
		}
		assertNull(JsonStoreImpl.namespace(Boolean::class).get())
	}
}
