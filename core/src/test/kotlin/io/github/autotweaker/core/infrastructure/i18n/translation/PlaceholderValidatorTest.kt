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

import io.github.autotweaker.core.TestServices
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceholderValidatorTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	@Test
	fun `identical non positional placeholders pass`() {
		assertTrue(PlaceholderValidator.validate("值：%s，数量：%d", "值：%s，数量：%d"))
	}
	
	@Test
	fun `different non positional placeholder count fails`() {
		assertFalse(PlaceholderValidator.validate("值：%s 和 %d", "值：%s"))
	}
	
	@Test
	fun `extra placeholder in translation fails`() {
		assertFalse(PlaceholderValidator.validate("值：%s", "值：%s 和 %d"))
	}
	
	@Test
	fun `no placeholders passes`() {
		assertTrue(PlaceholderValidator.validate("纯文本", "纯文本"))
	}
	
	@Test
	fun `blank translation fails`() {
		assertFalse(PlaceholderValidator.validate("值：%s", "   "))
	}
	
	@Test
	fun `identical positional placeholders pass`() {
		assertTrue(PlaceholderValidator.validate("a=%1\$s b=%2\$d", "b=%2\$d a=%1\$s"))
	}
	
	@Test
	fun `different positional placeholder set fails`() {
		assertFalse(PlaceholderValidator.validate("a=%1\$s b=%2\$d", "a=%1\$s"))
	}
	
	@Test
	fun `positional and non positional placeholder mismatch fails`() {
		assertFalse(PlaceholderValidator.validate("a=%1\$s", "a=%s"))
	}
	
	@Test
	fun `percent escape is not a placeholder`() {
		assertTrue(PlaceholderValidator.validate("100%%", "百分之一百%%"))
	}
}
