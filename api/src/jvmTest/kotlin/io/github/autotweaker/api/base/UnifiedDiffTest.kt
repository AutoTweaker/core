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

package io.github.autotweaker.api.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnifiedDiffTest {
	
	@Test
	fun `no difference returns null`() {
		assertNull(unifiedDiff("a\nb", "a\nb"))
	}
	
	@Test
	fun `empty contents return null`() {
		assertNull(unifiedDiff(null, ""))
		assertNull(unifiedDiff("", ""))
	}
	
	@Test
	fun `modify line`() {
		val diff = unifiedDiff("line1\nline2\nline3", "line1\nline2x\nline3")
		assertEquals(
			"@@ -2 +2 @@\n" +
					"-line2\n" +
					"+line2x",
			diff
		)
	}
	
	@Test
	fun `single line change omits count`() {
		val diff = unifiedDiff("old", "new")
		assertEquals(
			"@@ -1 +1 @@\n" +
					"-old\n" +
					"+new",
			diff
		)
	}
	
	@Test
	fun `new file starts at zero`() {
		val diff = unifiedDiff(null, "a\nb")
		assertEquals(
			"@@ -0,0 +1,2 @@\n" +
					"+a\n" +
					"+b",
			diff
		)
	}
	
	@Test
	fun `empty old content treated as new file`() {
		val diff = unifiedDiff("", "a")
		assertEquals(
			"@@ -0,0 +1 @@\n" +
					"+a",
			diff
		)
	}
	
	@Test
	fun `insert at start keeps old position`() {
		val diff = unifiedDiff("b", "a\nb")
		assertEquals(
			"@@ -1,0 +1 @@\n" +
					"+a",
			diff
		)
	}
	
	@Test
	fun `delete line`() {
		val diff = unifiedDiff("a\nb\nc", "a\nc")
		assertEquals(
			"@@ -2 +2,0 @@\n" +
					"-b",
			diff
		)
	}
	
	@Test
	fun `delete to empty side keeps zero count`() {
		val diff = unifiedDiff("a\nb", "a")
		assertEquals(
			"@@ -2 +2,0 @@\n" +
					"-b",
			diff
		)
	}
	
	@Test
	fun `multiple hunks separated without blank line`() {
		val diff = unifiedDiff("a\nb\nc\nd\ne\nf\ng", "a\nX\nc\nd\nY\nf\ng")
		assertEquals(
			"@@ -2 +2 @@\n" +
					"-b\n" +
					"+X\n" +
					"@@ -5 +5 @@\n" +
					"-e\n" +
					"+Y",
			diff
		)
	}
}
