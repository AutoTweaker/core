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

import io.github.autotweaker.core.infrastructure.i18n.translation.PlaceholderValidator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceholderValidatorTest {
	@Test
	fun `validates matching simple placeholders`() {
		assertTrue(PlaceholderValidator.validate("Name: %s", "Name: %s"))
		assertTrue(PlaceholderValidator.validate("Count: %d", "Count: %d"))
	}
	
	@Test
	fun `validates positional placeholders with different order`() {
		assertTrue(
			PlaceholderValidator.validate(
				$$"%1$s deleted %2$s", $$"deleted %2$s from %1$s"
			)
		)
	}
	
	@Test
	fun `rejects missing positional placeholder`() {
		assertFalse(
			PlaceholderValidator.validate(
				$$"%1$s %2$s", $$"%1$s %1$s"
			)
		)
	}
	
	@Test
	fun `allows duplicate positional if set matches`() {
		assertTrue(
			PlaceholderValidator.validate(
				$$"%1$s %1$s", $$"%1$s"
			)
		)
	}
	
	@Test
	fun `rejects blank translation`() {
		assertFalse(PlaceholderValidator.validate("Hello %s", ""))
		assertFalse(PlaceholderValidator.validate("Hello %s", "   "))
	}
	
	@Test
	fun `rejects missing nonPositional placeholder`() {
		assertFalse(PlaceholderValidator.validate("Name: %s, Count: %d", "Name: %s"))
	}
	
	@Test
	fun `validates text without placeholders`() {
		assertTrue(PlaceholderValidator.validate("Hello", "Hello"))
	}
}
