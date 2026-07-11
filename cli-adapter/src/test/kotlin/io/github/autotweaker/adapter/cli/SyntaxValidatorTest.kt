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

package io.github.autotweaker.adapter.cli

import io.github.autotweaker.adapter.cli.commands.Param
import io.github.autotweaker.adapter.cli.commands.Syntax
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyntaxValidatorTest {
	
	private fun flag(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Flag(name, name, defaultAlias(name)), required = required)
	
	private fun value(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Value(name, name, defaultAlias(name)), required = required)
	
	private fun positional(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Positional(name, name), required = required)
	
	private fun defaultAlias(name: String) = if (name.length > 1) listOf(name[0].toString()) else emptyList()
	
	private fun all(vararg children: Syntax, required: Boolean = true) = Syntax.All(children.toList(), required)
	
	private fun xor(vararg children: Syntax, required: Boolean = true) = Syntax.Xor(children.toList(), required)
	
	private fun validate(syntax: Syntax, active: Set<String>, posCount: Int = 0): Boolean =
		SyntaxValidator.validate(syntax, active, posCount)
	
	// ── leaf ──────────────────────────────────────────────────────
	
	@Test
	fun optionalLeafAbsent() {
		assertTrue(validate(all(flag("v")), emptySet()))
	}
	
	@Test
	fun optionalLeafPresent() {
		assertTrue(validate(all(flag("v")), setOf("v")))
	}
	
	@Test
	fun requiredLeafAbsent() {
		assertFalse(validate(all(flag("v", required = true)), emptySet()))
	}
	
	@Test
	fun requiredLeafPresent() {
		assertTrue(validate(all(flag("v", required = true)), setOf("v")))
	}
	
	// ── all group ─────────────────────────────────────────────────
	
	@Test
	fun allRequiredNoActiveChildrenFails() {
		assertFalse(validate(all(flag("a", required = true), flag("b", required = true)), emptySet()))
	}
	
	@Test
	fun allRequiredAllPresent() {
		assertTrue(validate(all(flag("a", required = true), flag("b", required = true)), setOf("a", "b")))
	}
	
	@Test
	fun allRequiredPartialFails() {
		assertFalse(validate(all(flag("a", required = true), flag("b", required = true)), setOf("a")))
	}
	
	@Test
	fun allOptionalGroupEmpty() {
		assertTrue(validate(all(flag("a"), flag("b"), required = false), emptySet()))
	}
	
	@Test
	fun allWithOnlyOptionalChildrenPassesWithoutActive() {
		assertTrue(validate(all(flag("a"), flag("b")), emptySet()))
	}
	
	@Test
	fun allOptionalGroupWithRequiredLeafFailsWhenLeafAbsent() {
		assertFalse(validate(all(flag("must", required = true), required = false), emptySet()))
	}
	
	@Test
	fun allOptionalGroupWithRequiredLeafPassesWhenLeafPresent() {
		assertTrue(validate(all(flag("must", required = true), required = false), setOf("must")))
	}
	
	// ── xor group ─────────────────────────────────────────────────
	
	@Test
	fun xorExactlyOnePasses() {
		assertTrue(validate(xor(flag("a"), flag("b")), setOf("a")))
	}
	
	@Test
	fun xorZeroFailsWhenRequired() {
		assertFalse(validate(xor(flag("a"), flag("b")), emptySet()))
	}
	
	@Test
	fun xorMoreThanOneFails() {
		assertFalse(validate(xor(flag("a"), flag("b")), setOf("a", "b")))
	}
	
	@Test
	fun xorOptionalAllowsZero() {
		assertTrue(validate(xor(flag("a"), flag("b"), required = false), emptySet()))
	}
	
	@Test
	fun xorOptionalStillRejectsMoreThanOne() {
		assertFalse(validate(xor(flag("a"), flag("b"), required = false), setOf("a", "b")))
	}
	
	// ── nested groups ─────────────────────────────────────────────
	
	@Test
	fun xorInsideAllPicksBranch() {
		val syntax = all(
			xor(flag("a"), flag("b")),
			flag("c"),
		)
		assertTrue(validate(syntax, setOf("a", "c")))
	}
	
	@Test
	fun xorInsideAllMissingXorChoice() {
		val syntax = all(
			xor(flag("a"), flag("b")),
			flag("c"),
		)
		assertFalse(validate(syntax, setOf("c")))
	}
	
	@Test
	fun allInsideXor() {
		val syntax = xor(
			flag("a"),
			all(flag("b"), flag("c", required = true)),
		)
		assertTrue(validate(syntax, setOf("a")))
		assertTrue(validate(syntax, setOf("b", "c")))
		assertFalse(validate(syntax, setOf("b")))
	}
	
	@Test
	fun threeLevelNestedXor() {
		val syntax = xor(
			all(
				flag("mode"),
				xor(flag("fast", required = true), flag("slow", required = true)),
			),
			all(flag("quiet", required = true)),
		)
		assertTrue(validate(syntax, setOf("mode", "fast")))
		assertTrue(validate(syntax, setOf("quiet")))
		assertFalse(validate(syntax, setOf("mode")))
	}
	
	@Test
	fun deepOptionalGroupSkipped() {
		val syntax = all(
			flag("verbose"),
			xor(flag("a", required = true), flag("b"), required = false),
		)
		assertTrue(validate(syntax, setOf("verbose")))
	}
	
	// ── positionals ───────────────────────────────────────────────
	
	@Test
	fun requiredPositionalSatisfied() {
		assertTrue(validate(all(positional("file", required = true)), emptySet(), posCount = 1))
	}
	
	@Test
	fun requiredPositionalMissing() {
		assertFalse(validate(all(positional("file", required = true)), emptySet(), posCount = 0))
	}
	
	@Test
	fun countRequiredPositional() {
		val syntax = all(
			positional("src", required = true),
			positional("dst", required = true),
		)
		assertEquals(2, SyntaxValidator.countRequiredPositional(syntax))
	}
	
	@Test
	fun countRequiredPositionalWithOptional() {
		val syntax = all(
			positional("src", required = true),
			positional("dst"),
		)
		assertEquals(1, SyntaxValidator.countRequiredPositional(syntax))
	}
	
	@Test
	fun countRequiredPositionalXorReturnsZero() {
		val syntax = xor(positional("a", required = true), positional("b", required = true))
		assertEquals(0, SyntaxValidator.countRequiredPositional(syntax))
	}
	
	// ── collectParams ─────────────────────────────────────────────
	
	@Test
	fun collectParamsAll() {
		val syntax = all(flag("a"), value("b"), positional("c"))
		val params = SyntaxValidator.collectParams(syntax)
		assertEquals(3, params.size)
	}
	
	@Test
	fun collectParamsXor() {
		val syntax = xor(flag("a"), flag("b"))
		val params = SyntaxValidator.collectParams(syntax)
		assertEquals(2, params.size)
	}
	
	// ── checkConflicts ────────────────────────────────────────────
	
	@Test
	fun noConflicts() {
		val syntax = all(flag("alpha"), flag("beta"))
		assertTrue(SyntaxValidator.checkConflicts(syntax).isEmpty())
	}
	
	@Test
	fun duplicateParamNameDetected() {
		val f = Param.Flag("same", "a", listOf("s"))
		val syntax = all(
			Syntax.Leaf(f, required = false),
			Syntax.Leaf(f, required = false),
		)
		val conflicts = SyntaxValidator.checkConflicts(syntax)
		assertTrue(conflicts.any { it.contains("Duplicate param name") })
	}
	
	@Test
	fun aliasConflictDetected() {
		val syntax = all(
			Syntax.Leaf(Param.Flag("alpha", "a", listOf("a")), required = false),
			Syntax.Leaf(Param.Flag("beta", "b", listOf("a")), required = false),
		)
		val conflicts = SyntaxValidator.checkConflicts(syntax)
		assertTrue(conflicts.any { it.contains("Alias conflict") })
	}
	
	// ── complex structures ────────────────────────────────────────
	
	@Test
	fun complexCfgListMode() {
		val syntax = xor(
			all(
				xor(
					all(flag("list", required = true), value("count")),
					all(
						flag("search", required = true),
						xor(flag("key"), flag("value"), flag("desc"), required = false)
					),
				),
				flag("full"),
			),
			all(flag("put", required = true), positional("val")),
		)
		assertTrue(validate(syntax, setOf("list", "full")))
		assertTrue(validate(syntax, setOf("search", "key", "full")))
		assertTrue(validate(syntax, setOf("put"), posCount = 1))
		assertFalse(validate(syntax, setOf("list", "search")))
		assertFalse(validate(syntax, setOf("search", "key", "value")))
	}
}
