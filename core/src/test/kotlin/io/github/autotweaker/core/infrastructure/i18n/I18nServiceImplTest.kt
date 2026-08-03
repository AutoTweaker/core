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

package io.github.autotweaker.core.infrastructure.i18n

import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.storage.JsonStore
import io.github.autotweaker.api.types.Localizations
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.*
import kotlin.test.*

class I18nServiceImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val registry = mutableMapOf<String, Localizations>()
	
	private fun entryFor(): JsonStore = mockk<JsonStore>().also {
		every { it.get() } answers { null }
		every { it.set(any()) } answers { }
	}
	
	private class TestDef(override val localizations: Localizations) : I18nDef
	
	private fun def(localizations: Localizations) = TestDef(localizations)
	
	@BeforeTest
	fun setUp() {
		registry.clear()
		mockkObject(I18nRegistry)
		every { I18nRegistry.get(any()) } answers { registry[firstArg<String>()] }
		every { I18nRegistry.getAll() } returns registry
		mockkObject(JsonStoreImpl)
		every { JsonStoreImpl.namespace(any()) } answers { entryFor() }
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(I18nRegistry)
		unmockkObject(JsonStoreImpl)
	}
	
	@Test
	fun `set and get language round trip`() {
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals(Locale.CHINESE, I18nServiceImpl.getLanguage())
	}
	
	@Test
	fun `invoke resolves registry default text`() {
		val d = def(mapOf(Locale.ENGLISH to "Hello", Locale.CHINESE to "你好"))
		registry[d::class.qualifiedName!!] = d.localizations
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals("你好", I18nServiceImpl.invoke(d))
	}
	
	@Test
	fun `set overrides registry text`() {
		val key = "test.override"
		registry[key] = mapOf(Locale.ENGLISH to "Hello")
		
		I18nServiceImpl.set(key, "自定义", Locale.CHINESE)
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals("自定义", I18nServiceImpl.resolveByKey(key))
	}
	
	@Test
	fun `resolve falls back to same language`() {
		val key = "test.fallback-same-language"
		registry[key] = mapOf(Locale.UK to "British")
		
		I18nServiceImpl.setLanguage(Locale.US)
		
		assertEquals("British", I18nServiceImpl.resolveByKey(key))
	}
	
	@Test
	fun `resolve falls back to english`() {
		val key = "test.fallback-english"
		registry[key] = mapOf(Locale.ENGLISH to "English text")
		
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals("English text", I18nServiceImpl.resolveByKey(key))
	}
	
	@Test
	fun `resolve falls back to first entry`() {
		val key = "test.fallback-first"
		registry[key] = mapOf(Locale.FRENCH to "Français")
		
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals("Français", I18nServiceImpl.resolveByKey(key))
	}
	
	@Test
	fun `resolve returns key when localization missing`() {
		val d = def(emptyMap())
		
		I18nServiceImpl.setLanguage(Locale.CHINESE)
		
		assertEquals(d::class.qualifiedName, I18nServiceImpl.invoke(d))
	}
	
	@Test
	fun `set unknown key fails`() {
		assertFailsWith<IllegalStateException> {
			I18nServiceImpl.set("com.unknown.Key", "text", Locale.ENGLISH)
		}
	}
	
	@Test
	fun `getAllEntries merges registry with overrides`() {
		val key = "test.merge"
		registry[key] = mapOf(Locale.ENGLISH to "Hello")
		
		I18nServiceImpl.set(key, "你好", Locale.CHINESE)
		
		val all = I18nServiceImpl.getAllEntries()
		assertEquals("Hello", all[key]?.get(Locale.ENGLISH))
		assertEquals("你好", all[key]?.get(Locale.CHINESE))
	}
}
