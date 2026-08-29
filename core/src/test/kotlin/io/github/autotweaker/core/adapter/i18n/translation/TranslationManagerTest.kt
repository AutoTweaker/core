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

import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationEngine
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.*

class TranslationManagerTest {
	
	private val stored = mutableMapOf<KClass<*>, JsonElement?>()
	
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private lateinit var manager: TranslationManager
	
	@BeforeTest
	fun setUp() {
		stored.clear()
		every { TestServices.jsonStore.namespace(any()) } answers {
			mockk<JsonStore>().also {
				every { it.get() } answers { stored[TranslationManager::class] }
				every { it.set(any()) } answers { stored[TranslationManager::class] = firstArg<JsonElement>() }
			}
		}
		manager = TranslationManager(
			modelResolver = mockk<ModelResolver>(relaxed = true),
			engine = mockk<TranslationEngine>(relaxed = true),
		)
	}
	
	@AfterTest
	fun tearDown() = runBlocking {
		manager.setModel(null)
	}
	
	@Test
	fun `setModel and getModel roundtrip`() = runBlocking {
		val id = UUID.randomUUID()
		manager.setModel(id)
		assertEquals(id, manager.getModel())
	}
	
	@Test
	fun `setModel null clears model`() = runBlocking {
		manager.setModel(UUID.randomUUID())
		manager.setModel(null)
		assertNull(manager.getModel())
	}
	
	@Test
	fun `status is a StateFlow`() {
		val status = manager.status.value
		assertTrue(status == TranslationStatus.IDLE || status == TranslationStatus.TRANSLATING)
	}
}
