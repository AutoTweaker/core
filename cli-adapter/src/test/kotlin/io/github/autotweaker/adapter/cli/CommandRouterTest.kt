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

import io.github.autotweaker.adapter.cli.Command.Chunk
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.config.SettingValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterTest {
	
	private val core = mockk<CoreAPI>(relaxed = true)
	private val commands = mutableListOf<Command>()
	private lateinit var router: CommandRouter
	
	@BeforeEach
	fun setUp() {
		commands.clear()
		val config = mockk<CoreAPI.ConfigAPI>(relaxed = true)
		val settingService = mockk<SettingService>(relaxed = true)
		every { core.config } returns config
		every { config.settingService() } returns settingService
		every { settingService.get<SettingValue.ValInt>(any()) } returns SettingValue.ValInt(100_000)
		router = CommandRouter(core, SemVer.parse("1.0.0"), commands)
	}
	
	private fun registerCommand(name: String, syntax: Syntax, handle: (Request) -> List<Chunk>): Command {
		val cmd = mockk<Command>()
		every { cmd.name } returns name
		every { cmd.description } returns ""
		every { cmd.syntax } returns syntax
		every { cmd.init(any(), any()) } returns Unit
		every { cmd.handle(any(), any()) } answers {
			val request = firstArg<Request>()
			flowOf(*(handle(request).toTypedArray()))
		}
		commands.add(cmd)
		router = CommandRouter(core, SemVer.parse("1.0.0"), commands)
		return cmd
	}
	
	@Suppress("SameParameterValue")
	private fun simpleCommand(name: String, syntax: Syntax): Command =
		registerCommand(name, syntax) { listOf(Chunk.Done(0)) }
	
	private fun dispatch(vararg args: String): List<Chunk> = runBlocking {
		router.dispatch(
			CliMessage.Command(args = args.toList()),
		) { _, _ -> "" }.toList()
	}
	
	private fun List<Chunk>.done(): Chunk.Done = last() as Chunk.Done
	private fun List<Chunk>.stdout(): List<String> =
		filter { it is Chunk.Data && it.channel == Chunk.Channel.STDOUT }.map { (it as Chunk.Data).text }
	
	private fun List<Chunk>.stderr(): List<String> =
		filter { it is Chunk.Data && it.channel == Chunk.Channel.STDERR }.map { (it as Chunk.Data).text }
	
	@Test
	fun `empty command shows copyright`() {
		val r = runBlocking {
			router.dispatch(CliMessage.Command(args = emptyList())) { _, _ -> "" }.toList()
		}
		assertEquals(0, r.done().exitCode)
		assertTrue(r.stdout().any { it.contains("AutoTweaker") })
	}
	
	@Test
	fun `unknown command returns error`() {
		val r = dispatch("nonexistent")
		assertEquals(1, r.done().exitCode)
		assertTrue(r.stderr().isNotEmpty())
	}
	
	@Test
	fun `command with no params`() {
		simpleCommand("test", Syntax.none())
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `none syntax rejects extra args`() {
		simpleCommand("test", Syntax.none())
		assertEquals(1, dispatch("test", "--extra").done().exitCode)
	}
	
	@Test
	fun `short flag parsed`() {
		var captured: Request? = null
		registerCommand("test", Syntax.all(Syntax.leaf(Param.Flag("verbose", "verbose")))) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-v")
		assertTrue(captured!!.has("verbose"))
	}
	
	@Test
	fun `long flag parsed`() {
		var captured: Request? = null
		registerCommand("test", Syntax.all(Syntax.leaf(Param.Flag("verbose", "verbose")))) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "--verbose")
		assertTrue(captured!!.has("verbose"))
	}
	
	@Test
	fun `alias resolves to canonical name`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-v")
		// alias "v" (auto-inferred) resolves to canonical "verbose"
		assertTrue(captured!!.has("v"))
		assertTrue(captured.has("verbose"))
	}
	
	@Test
	fun `short value parsed`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Value("count", "count")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-c", "42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `long value with equals`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Value("count", "count")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "--count=42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `short value with equals`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Value("count", "count")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-c=42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `bundled short flags`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-ab")
		assertTrue(captured!!.has("alpha"))
		assertTrue(captured.has("beta"))
	}
	
	@Test
	fun `end of options marker`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("file", "file")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "--", "--verbose")
		assertEquals(listOf("--verbose"), captured!!.positional)
	}
	
	@Test
	fun `flag with equals rejected`() {
		simpleCommand("test", Syntax.all(Syntax.leaf(Param.Flag("verbose", "verbose"))))
		assertEquals(1, dispatch("test", "--verbose=value").done().exitCode)
	}
	
	@Test
	fun `unknown option rejected`() {
		simpleCommand("test", Syntax.all(Syntax.leaf(Param.Flag("verbose", "verbose"))))
		assertEquals(1, dispatch("test", "--unknown").done().exitCode)
	}
	
	@Test
	fun `positional collected`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("file", "file")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "myfile.txt")
		assertEquals(listOf("myfile.txt"), captured!!.positional)
	}
	
	@Test
	fun `positional too many rejected`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("single", "single")),
			)
		)
		assertEquals(1, dispatch("test", "a", "b").done().exitCode)
	}
	
	@Test
	fun `all required passes without active children`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose")),
				required = false,
			)
		)
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `all required fails without active children`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose")),
				required = true,
			)
		)
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `required leaf passes when present`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose"), required = true),
			)
		)
		assertEquals(0, dispatch("test", "--verbose").done().exitCode)
	}
	
	@Test
	fun `required leaf fails when absent`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose"), required = true),
			)
		)
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor exactly one passes`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "-a")
		assertTrue(captured!!.has("alpha"))
	}
	
	@Test
	fun `xor zero fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
			)
		)
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor more than one fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
			)
		)
		assertEquals(1, dispatch("test", "-a", "-b").done().exitCode)
	}
	
	@Test
	fun `xor required false allows zero`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
				required = false,
			)
		)
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor required false still rejects more than one`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("alpha", "alpha")),
				Syntax.leaf(Param.Flag("beta", "beta")),
				required = false,
			)
		)
		assertEquals(1, dispatch("test", "-a", "-b").done().exitCode)
	}
	
	@Test
	fun `nested groups validated`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.xor(
					Syntax.leaf(Param.Flag("alpha", "alpha")),
					Syntax.all(
						Syntax.leaf(Param.Flag("beta", "beta")),
						Syntax.leaf(Param.Flag("gamma", "gamma"), required = true),
					),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// Pick alpha branch
		dispatch("test", "-a")
		assertTrue(captured!!.has("alpha"))
	}
	
	// ── deeper trees ────────────────────────────────────────────
	@Test
	fun `three level tree xor inside all inside xor`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("mode", "mode")),
					Syntax.xor(
						Syntax.leaf(Param.Flag("fast", "fast"), required = true),
						Syntax.leaf(Param.Flag("slow", "slow"), required = true),
					),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("quiet", "quiet"), required = true),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// Pick first branch, fast variant
		dispatch("test", "-m", "--fast")
		assertTrue(captured!!.has("mode"))
		assertTrue(captured.has("fast"))
	}
	
	@Test
	fun `three level inner xor fails without required`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("mode", "mode")),
					Syntax.xor(
						Syntax.leaf(Param.Flag("fast", "fast"), required = true),
						Syntax.leaf(Param.Flag("slow", "slow"), required = true),
					),
				),
				Syntax.leaf(Param.Flag("quiet", "quiet")),
			)
		)
		// mode without fast or slow fails inner xor
		assertEquals(1, dispatch("test", "--mode").done().exitCode)
	}
	
	@Test
	fun `deep optional group skipped`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "verbose")),
				Syntax.xor(
					Syntax.leaf(Param.Flag("alpha", "alpha"), required = true),
					Syntax.leaf(Param.Flag("beta", "beta")),
					required = false,
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// verbose only, optional xor skipped
		dispatch("test", "--verbose")
		assertTrue(captured!!.has("verbose"))
	}
	
	// ── same-name params across branches ────────────────────────
	@Test
	fun `same name same type in xor branches works`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("mode", "mode")),
					Syntax.leaf(Param.Flag("detail", "detail")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("quiet", "quiet")),
					Syntax.leaf(Param.Flag("info", "info")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// detail in first branch
		dispatch("test", "--mode", "--detail")
		assertTrue(captured!!.has("mode"))
		assertTrue(captured.has("detail"))
	}
	
	@Test
	fun `same name same type picks first for lookup`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.leaf(Param.Flag("flag", "version a")),
				Syntax.leaf(Param.Flag("tag", "version b")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// flag is present in both branches, distinctBy keeps first
		dispatch("test", "-f")
		assertTrue(captured!!.has("flag"))
	}
	
	@Test
	fun `same name with positional in all branches`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("src", "src")),
				Syntax.leaf(Param.Positional("dst", "dst")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "data.txt", "other.txt")
		assertEquals(listOf("data.txt", "other.txt"), captured!!.positional)
	}
	
	// ── complex combinations ────────────────────────────────────
	@Test
	fun `xor of all groups each with multiple leaves`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("alpha", "")),
					Syntax.leaf(Param.Flag("beta", "")),
					Syntax.leaf(Param.Value("gamma", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("mode", ""), required = true),
					Syntax.leaf(Param.Flag("delta", "")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// Pick second branch
		dispatch("test", "--mode")
		assertTrue(captured!!.has("mode"))
	}
	
	@Test
	fun `xor picks second branch with value`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("alpha", "")),
					Syntax.leaf(Param.Flag("beta", "")),
					Syntax.leaf(Param.Value("gamma", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("mode", ""), required = true),
					Syntax.leaf(Param.Flag("delta", "")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "--mode")
		assertTrue(captured!!.has("mode"))
	}
	
	@Test
	fun `mixed positional and flags in all group`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("verbose", "")),
				Syntax.leaf(Param.Positional("file", "")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "--verbose", "input.txt")
		assertTrue(captured!!.has("verbose"))
		assertEquals(listOf("input.txt"), captured.positional)
	}
	
	@Test
	fun `positional count exceeds declared`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("single", "")),
			)
		)
		// 2 positionals, only 1 declared
		assertEquals(1, dispatch("test", "a", "b").done().exitCode)
	}
	
	@Test
	fun `required positional with count check`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("file", ""), required = true),
			)
		)
		// 0 positionals, 1 required
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `required positional satisfied`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("file", ""), required = true),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "input.txt")
		assertEquals(listOf("input.txt"), captured!!.positional)
	}
	
	// ── edge cases ──────────────────────────────────────────────
	@Test
	fun `multiple positionals ordered correctly`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("src", "")),
				Syntax.leaf(Param.Positional("dst", "")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		dispatch("test", "a.txt", "b.txt")
		assertEquals(listOf("a.txt", "b.txt"), captured!!.positional)
	}
	
	@Test
	fun `flag after positional treated as positional`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Positional("file", "")),
				Syntax.leaf(Param.Flag("verbose", "")),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// "--verbose" is always parsed as a flag regardless of position
		dispatch("test", "file.txt", "--verbose")
		assertEquals(listOf("file.txt"), captured!!.positional)
		assertTrue(captured.has("verbose"))
	}
	
	@Test
	fun `all required false with required leaf inside fails when leaf absent`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("must", ""), required = true),
				required = false,
			)
		)
		// All is optional but leaf required still fails when absent
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `all required false with required leaf inside and leaf present passes`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.leaf(Param.Flag("must", ""), required = true),
				required = false,
			)
		)
		assertEquals(0, dispatch("test", "--must").done().exitCode)
	}
	
	@Test
	fun `complex cfg-like structure xors nested`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.xor(
						Syntax.all(
							Syntax.leaf(Param.Flag("list", ""), required = true),
							Syntax.leaf(Param.Value("count", "")),
						),
						Syntax.all(
							Syntax.leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.leaf(Param.Flag("key", "")),
								Syntax.leaf(Param.Flag("value", "")),
								Syntax.leaf(Param.Flag("desc", "")),
								required = false,
							),
						),
					),
					Syntax.leaf(Param.Flag("full", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("put", ""), required = true),
					Syntax.leaf(Param.Positional("value", "")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// list mode with full
		dispatch("test", "--list", "--full")
		assertTrue(captured!!.has("list"))
		assertTrue(captured.has("full"))
	}
	
	@Test
	fun `complex structure set mode`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.xor(
						Syntax.all(
							Syntax.leaf(Param.Flag("list", ""), required = true),
							Syntax.leaf(Param.Value("count", "")),
						),
						Syntax.all(
							Syntax.leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.leaf(Param.Flag("key", "")),
								Syntax.leaf(Param.Flag("value", "")),
								Syntax.leaf(Param.Flag("desc", "")),
								required = false,
							),
						),
					),
					Syntax.leaf(Param.Flag("full", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("put", ""), required = true),
					Syntax.leaf(Param.Positional("value", "")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// set mode
		dispatch("test", "--put", "newval")
		assertTrue(captured!!.has("put"))
		assertEquals(listOf("newval"), captured.positional)
	}
	
	@Test
	fun `complex structure search mode with filter`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.xor(
						Syntax.all(
							Syntax.leaf(Param.Flag("list", ""), required = true),
							Syntax.leaf(Param.Value("count", "")),
						),
						Syntax.all(
							Syntax.leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.leaf(Param.Flag("key", "")),
								Syntax.leaf(Param.Flag("value", "")),
								Syntax.leaf(Param.Flag("desc", "")),
								required = false,
							),
						),
					),
					Syntax.leaf(Param.Flag("full", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("put", ""), required = true),
					Syntax.leaf(Param.Positional("value", "")),
				),
			)
		) {
			captured = it; listOf(Chunk.Done(0))
		}
		// search mode with key filter, full detail
		dispatch("test", "--search", "--key", "--full")
		assertTrue(captured!!.has("search"))
		assertTrue(captured.has("key"))
		assertTrue(captured.has("full"))
	}
	
	@Test
	fun `complex structure search with two filters rejected`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.xor(
						Syntax.all(
							Syntax.leaf(Param.Flag("list", ""), required = true),
							Syntax.leaf(Param.Value("count", "")),
						),
						Syntax.all(
							Syntax.leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.leaf(Param.Flag("key", "")),
								Syntax.leaf(Param.Flag("value", "")),
								Syntax.leaf(Param.Flag("desc", "")),
								required = false,
							),
						),
					),
					Syntax.leaf(Param.Flag("full", "")),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("put", ""), required = true),
					Syntax.leaf(Param.Positional("value", "")),
				),
			)
		)
		assertEquals(1, dispatch("test", "--search", "--key", "--value").done().exitCode)
	}
}
