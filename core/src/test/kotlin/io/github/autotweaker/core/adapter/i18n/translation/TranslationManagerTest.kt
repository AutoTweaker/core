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

package io.github.autotweaker.core.adapter.i18n.translation

import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class TranslationManagerTest {
	
	private val dbUrl = "jdbc:h2:mem:tm_${counter.getAndIncrement()};DB_CLOSE_DELAY=-1"
	
	companion object {
		private val counter = AtomicInteger(0)
		
		init {
			TestServices.init()
		}
	}
	
	@BeforeTest
	fun setUp() {
		val databaseStore = mockk<DatabaseStore>()
		every { databaseStore.connect(any()) } answers {
			Database.connect(dbUrl, "org.h2.Driver")
		}
		JsonStoreImpl.init(databaseStore)
	}
	
	@AfterTest
	fun tearDown() {
		TranslationManager.setModel(null)
	}
	
	@Test
	fun `setModel and getModel roundtrip`() {
		val id = UUID.randomUUID()
		TranslationManager.setModel(id)
		assertEquals(id, TranslationManager.getModel())
	}
	
	@Test
	fun `setModel null clears model`() {
		TranslationManager.setModel(UUID.randomUUID())
		TranslationManager.setModel(null)
		assertNull(TranslationManager.getModel())
	}
	
	@Test
	fun `status is a StateFlow`() {
		val status = TranslationManager.status.value
		assertTrue(status == TranslationStatus.IDLE || status == TranslationStatus.TRANSLATING)
	}
}
