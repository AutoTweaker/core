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

import kotlin.test.*

class ArgParserTest {
	
	private val parser = ArgParser(100_000)
	
	private fun parse(syntax: Syntax, vararg args: String): Request? =
		parser.parse(args.toList(), syntax, "prog")
	
	private fun flag(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Flag(name, name), required = required)
	
	private fun value(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Value(name, name), required = required)
	
	private fun positional(name: String, required: Boolean = false) =
		Syntax.Leaf(Param.Positional(name, name), required = required)
	
	private fun flagWithAlias(name: String, aliases: List<String>, required: Boolean = false) =
		Syntax.Leaf(Param.Flag(name, name, aliases), required = required)
	
	private fun valueWithAlias(name: String, aliases: List<String>, required: Boolean = false) =
		Syntax.Leaf(Param.Value(name, name, aliases), required = required)
	
	// ── flag parsing ──────────────────────────────────────────────
	
	@Test
	fun longFlag() {
		val r = parse(Syntax.all(flag("verbose")), "--verbose")
		assertNotNull(r)
		assertTrue(r.has("verbose"))
	}
	
	@Test
	fun shortFlag() {
		val r = parse(Syntax.all(flag("verbose")), "-v")
		assertNotNull(r)
		assertTrue(r.has("verbose"))
	}
	
	@Test
	fun bundledShortFlags() {
		val r = parse(Syntax.all(flag("alpha"), flag("beta")), "-ab")
		assertNotNull(r)
		assertTrue(r.has("alpha"))
		assertTrue(r.has("beta"))
	}
	
	@Test
	fun flagWithEqualsRejected() {
		assertNull(parse(Syntax.all(flag("verbose")), "--verbose=value"))
	}
	
	@Test
	fun unknownFlagRejected() {
		assertNull(parse(Syntax.all(flag("verbose")), "--unknown"))
	}
	
	// ── value parsing ─────────────────────────────────────────────
	
	@Test
	fun longValueWithEquals() {
		val r = parse(Syntax.all(value("count")), "--count=42")
		assertNotNull(r)
		assertEquals("42", r.get("count"))
	}
	
	@Test
	fun longValueWithSpace() {
		val r = parse(Syntax.all(value("count")), "--count", "42")
		assertNotNull(r)
		assertEquals("42", r.get("count"))
	}
	
	@Test
	fun shortValueWithSpace() {
		val r = parse(Syntax.all(value("count")), "-c", "42")
		assertNotNull(r)
		assertEquals("42", r.get("count"))
	}
	
	@Test
	fun shortValueWithEquals() {
		val r = parse(Syntax.all(value("count")), "-c=42")
		assertNotNull(r)
		assertEquals("42", r.get("count"))
	}
	
	@Test
	fun valueMissingArgRejected() {
		assertNull(parse(Syntax.all(value("count")), "--count"))
	}
	
	@Test
	fun valueEndOfOptionsRejected() {
		assertNull(parse(Syntax.all(value("count")), "-f", "--"))
	}
	
	// ── alias resolution ──────────────────────────────────────────
	
	@Test
	fun aliasResolvesToCanonical() {
		val r = parse(Syntax.all(flagWithAlias("verbose", listOf("v"))), "-v")
		assertNotNull(r)
		assertTrue(r.has("v"))
		assertTrue(r.has("verbose"))
	}
	
	@Test
	fun aliasForValue() {
		val r = parse(Syntax.all(valueWithAlias("count", listOf("c"))), "-c", "10")
		assertNotNull(r)
		assertEquals("10", r.get("count"))
		assertEquals("10", r.get("c"))
	}
	
	// ── positional args ───────────────────────────────────────────
	
	@Test
	fun positionalCollected() {
		val r = parse(Syntax.all(positional("file")), "myfile.txt")
		assertNotNull(r)
		assertEquals(listOf("myfile.txt"), r.positional)
	}
	
	@Test
	fun multiplePositionals() {
		val r = parse(Syntax.all(positional("src"), positional("dst")), "a.txt", "b.txt")
		assertNotNull(r)
		assertEquals(listOf("a.txt", "b.txt"), r.positional)
	}
	
	@Test
	fun positionalTooManyRejected() {
		assertNull(parse(Syntax.all(positional("single")), "a", "b"))
	}
	
	@Test
	fun requiredPositionalMissingRejected() {
		assertNull(parse(Syntax.all(positional("file", required = true))))
	}
	
	@Test
	fun optionalPositionalOmitted() {
		val r = parse(Syntax.all(positional("file")))
		assertNotNull(r)
		assertTrue(r.positional.isEmpty())
	}
	
	// ── end of options ────────────────────────────────────────────
	
	@Test
	fun endOfOptionsMarker() {
		val r = parse(Syntax.all(positional("file")), "--", "--verbose")
		assertNotNull(r)
		assertEquals(listOf("--verbose"), r.positional)
	}
	
	@Test
	fun endOfOptionsStopsParsing() {
		val r = parse(Syntax.all(flag("verbose"), positional("file")), "--", "--verbose")
		assertNotNull(r)
		assertEquals(listOf("--verbose"), r.positional)
		assertTrue(!r.has("verbose"))
	}
	
	// ── mixed ─────────────────────────────────────────────────────
	
	@Test
	fun flagAndPositional() {
		val r = parse(Syntax.all(flag("verbose"), positional("file")), "--verbose", "input.txt")
		assertNotNull(r)
		assertTrue(r.has("verbose"))
		assertEquals(listOf("input.txt"), r.positional)
	}
	
	@Test
	fun valueAndPositional() {
		val r = parse(Syntax.all(value("count"), positional("file")), "--count", "5", "data.txt")
		assertNotNull(r)
		assertEquals("5", r.get("count"))
		assertEquals(listOf("data.txt"), r.positional)
	}
	
	// ── none syntax ───────────────────────────────────────────────
	
	@Test
	fun noneSyntaxNoArgs() {
		assertNotNull(parse(Syntax.none()))
	}
	
	@Test
	fun noneSyntaxExtraArgsRejected() {
		assertNull(parse(Syntax.none(), "--extra"))
	}
	
	// ── max args ──────────────────────────────────────────────────
	
	@Test
	fun exceedsMaxArgsRejected() {
		val smallParser = ArgParser(2)
		assertNull(smallParser.parse(listOf("a", "b", "c"), Syntax.none(), "prog"))
	}
}
