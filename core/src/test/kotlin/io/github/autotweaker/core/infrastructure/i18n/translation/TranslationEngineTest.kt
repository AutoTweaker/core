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

package io.github.autotweaker.core.infrastructure.i18n.translation

import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.api.types.Localizations
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.application.impl.ChatService
import io.github.autotweaker.core.infrastructure.i18n.I18nRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.*
import kotlin.test.*

class TranslationEngineTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val registry = mutableMapOf<String, Localizations>()
	private lateinit var engine: TranslationEngine
	
	private fun entryFor(): JsonStore = mockk<JsonStore>().also {
		every { it.get() } answers { null }
		every { it.set(any()) } answers { }
	}
	
	@BeforeTest
	fun setUp() {
		registry.clear()
		mockkObject(I18nRegistry)
		every { I18nRegistry.get(any()) } answers { registry[firstArg<String>()] }
		every { I18nRegistry.getAll() } returns registry
		every { TestServices.jsonStore.namespace(any()) } answers { entryFor() }
		engine = TranslationEngine(mockk<ChatService>(relaxed = true))
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(I18nRegistry)
	}
	
	@Test
	fun `exact locale match is completed`() {
		registry["test.exact"] = mapOf(zh to "你好")
		
		assertTrue(engine.isCompleted(Locale.CHINA))
	}
	
	@Test
	fun `same language without country is completed`() {
		registry["test.language"] = mapOf(zh to "你好")
		
		assertTrue(engine.isCompleted(Locale.CHINESE))
	}
	
	@Test
	fun `explicit script of same locale is completed`() {
		registry["test.script"] = mapOf(zh to "你好")
		
		assertTrue(engine.isCompleted(Locale.forLanguageTag("zh-Hans-CN")))
	}
	
	@Test
	fun `same language with country does not complete`() {
		registry["test.country"] = mapOf(zh to "你好")
		
		assertFalse(engine.isCompleted(Locale.TAIWAN))
	}
	
	@Test
	fun `different script is not completed`() {
		registry["test.script-diff"] = mapOf(Locale.forLanguageTag("zh-Hans-CN") to "你好")
		
		assertFalse(engine.isCompleted(Locale.forLanguageTag("zh-Hant-TW")))
	}
	
	@Test
	fun `exact locale with country is completed`() {
		registry["test.country-exact"] = mapOf(Locale.TAIWAN to "你好")
		
		assertTrue(engine.isCompleted(Locale.TAIWAN))
	}
	
	@Test
	fun `missing target language is not completed`() {
		registry["test.missing"] = mapOf(Locale.ENGLISH to "Hello")
		
		assertFalse(engine.isCompleted(Locale.CHINESE))
	}
	
	@Test
	fun `empty registry is completed`() {
		assertTrue(engine.isCompleted(Locale.CHINESE))
	}
}
