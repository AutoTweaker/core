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
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationManagerTest {
	
	@Test
	fun `updateModel and getModel roundtrip`() {
		val id = UUID.randomUUID()
		TranslationManager.updateModel(id)
		assertEquals(id, TranslationManager.getModel())
	}
	
	@Test
	fun `updateLanguage and getLanguage roundtrip`() {
		val locale = Locale.GERMANY
		TranslationManager.updateLanguage(locale)
		assertEquals(locale, TranslationManager.getLanguage())
	}
	
	@Test
	fun `status is a StateFlow`() {
		val status = TranslationManager.status.value
		assertTrue(status == TranslationStatus.IDLE || status == TranslationStatus.TRANSLATING)
	}
}
