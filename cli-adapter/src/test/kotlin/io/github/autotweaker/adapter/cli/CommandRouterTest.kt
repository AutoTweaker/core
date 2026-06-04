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

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.config.SettingValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterTest {
	
	private val core = mockk<CoreAPI>(relaxed = true)
	private val commands = mutableListOf<Command>()
	private lateinit var router: CommandRouter
	
	@BeforeTest
	fun setUp() {
		commands.clear()
		val config = mockk<CoreAPI.ConfigAPI>(relaxed = true)
		val settingService = mockk<SettingService>(relaxed = true)
		every { core.config } returns config
		every { config.settingService } returns settingService
		every { settingService.get<SettingValue.ValInt>(any()) } returns SettingValue.ValInt(100_000)
		val secret = mockk<CoreAPI.SecretAPI>()
		every { core.secret } returns secret
		every { secret.isUnlocked } returns MutableStateFlow(true)
		router = CommandRouter(core, SemVer.parse("1.0.0"), commands)
	}
	
	private fun registerCommand(name: String, syntax: Syntax, handle: (Request) -> List<CmdOutput>): Command {
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
		registerCommand(name, syntax) { listOf(CmdOutput.Done(0)) }
	
	private fun dispatch(vararg args: String): List<CmdOutput> = runBlocking {
		router.dispatch(
			CliMessage.Command(args = args.toList()),
		) { _, _ -> "" }.toList()
	}
	
	private fun List<CmdOutput>.done(): CmdOutput.Done = last() as CmdOutput.Done
	private fun List<CmdOutput>.stdout(): List<String> =
		filter { it is CmdOutput.Data && it.channel == CmdOutput.Channel.STDOUT }.map { (it as CmdOutput.Data).text }
	
	private fun List<CmdOutput>.stderr(): List<String> =
		filter { it is CmdOutput.Data && it.channel == CmdOutput.Channel.STDERR }.map { (it as CmdOutput.Data).text }
	
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
		registerCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false))) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "-v")
		assertTrue(captured!!.has("verbose"))
	}
	
	@Test
	fun `long flag parsed`() {
		var captured: Request? = null
		registerCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false))) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--verbose")
		assertTrue(captured!!.has("verbose"))
	}
	
	@Test
	fun `alias resolves to canonical name`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Value("count", "count"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "-c", "42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `long value with equals`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Value("count", "count"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--count=42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `short value with equals`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Value("count", "count"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "-c=42")
		assertEquals("42", captured!!.get("count"))
	}
	
	@Test
	fun `bundled short flags`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Positional("file", "file"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--", "--verbose")
		assertEquals(listOf("--verbose"), captured!!.positional)
	}
	
	@Test
	fun `flag with equals rejected`() {
		simpleCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false)))
		assertEquals(1, dispatch("test", "--verbose=value").done().exitCode)
	}
	
	@Test
	fun `unknown option rejected`() {
		simpleCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false)))
		assertEquals(1, dispatch("test", "--unknown").done().exitCode)
	}
	
	@Test
	fun `positional collected`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Positional("file", "file"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "myfile.txt")
		assertEquals(listOf("myfile.txt"), captured!!.positional)
	}
	
	@Test
	fun `positional too many rejected`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Positional("single", "single"), required = false),
			)
		)
		assertEquals(1, dispatch("test", "a", "b").done().exitCode)
	}
	
	@Test
	fun `all required passes without active children`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false),
				required = false,
			)
		)
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `all required with only optional children passes without active`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false),
				required = true,
			)
		)
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `required leaf passes when present`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = true),
			)
		)
		assertEquals(0, dispatch("test", "--verbose").done().exitCode)
	}
	
	@Test
	fun `required leaf fails when absent`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = true),
			)
		)
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor exactly one passes`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "-a")
		assertTrue(captured!!.has("alpha"))
	}
	
	@Test
	fun `xor zero fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
			)
		)
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor more than one fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
			)
		)
		assertEquals(1, dispatch("test", "-a", "-b").done().exitCode)
	}
	
	@Test
	fun `xor required false allows zero`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
				required = false,
			)
		)
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun `xor required false still rejects more than one`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
				Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
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
					Syntax.Leaf(Param.Flag("alpha", "alpha"), required = false),
					Syntax.all(
						Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
						Syntax.Leaf(Param.Flag("gamma", "gamma"), required = true),
					),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
					Syntax.Leaf(Param.Flag("mode", "mode"), required = false),
					Syntax.xor(
						Syntax.Leaf(Param.Flag("fast", "fast"), required = true),
						Syntax.Leaf(Param.Flag("slow", "slow"), required = true),
					),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("quiet", "quiet"), required = true),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
					Syntax.Leaf(Param.Flag("mode", "mode"), required = false),
					Syntax.xor(
						Syntax.Leaf(Param.Flag("fast", "fast"), required = true),
						Syntax.Leaf(Param.Flag("slow", "slow"), required = true),
					),
				),
				Syntax.Leaf(Param.Flag("quiet", "quiet"), required = false),
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
				Syntax.Leaf(Param.Flag("verbose", "verbose"), required = false),
				Syntax.xor(
					Syntax.Leaf(Param.Flag("alpha", "alpha"), required = true),
					Syntax.Leaf(Param.Flag("beta", "beta"), required = false),
					required = false,
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
					Syntax.Leaf(Param.Flag("mode", "mode"), required = false),
					Syntax.Leaf(Param.Flag("detail", "detail"), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("quiet", "quiet"), required = false),
					Syntax.Leaf(Param.Flag("info", "info"), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Flag("flag", "version a"), required = false),
				Syntax.Leaf(Param.Flag("tag", "version b"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Positional("src", "src"), required = false),
				Syntax.Leaf(Param.Positional("dst", "dst"), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
					Syntax.Leaf(Param.Flag("alpha", ""), required = false),
					Syntax.Leaf(Param.Flag("beta", ""), required = false),
					Syntax.Leaf(Param.Value("gamma", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("mode", ""), required = true),
					Syntax.Leaf(Param.Flag("delta", ""), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
					Syntax.Leaf(Param.Flag("alpha", ""), required = false),
					Syntax.Leaf(Param.Flag("beta", ""), required = false),
					Syntax.Leaf(Param.Value("gamma", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("mode", ""), required = true),
					Syntax.Leaf(Param.Flag("delta", ""), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--mode")
		assertTrue(captured!!.has("mode"))
	}
	
	@Test
	fun `mixed positional and flags in all group`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", ""), required = false),
				Syntax.Leaf(Param.Positional("file", ""), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--verbose", "input.txt")
		assertTrue(captured!!.has("verbose"))
		assertEquals(listOf("input.txt"), captured.positional)
	}
	
	@Test
	fun `positional count exceeds declared`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Positional("single", ""), required = false),
			)
		)
		// 2 positionals, only 1 declared
		assertEquals(1, dispatch("test", "a", "b").done().exitCode)
	}
	
	@Test
	fun `required positional with count check`() {
		simpleCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Positional("file", ""), required = true),
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
				Syntax.Leaf(Param.Positional("file", ""), required = true),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Positional("src", ""), required = false),
				Syntax.Leaf(Param.Positional("dst", ""), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "a.txt", "b.txt")
		assertEquals(listOf("a.txt", "b.txt"), captured!!.positional)
	}
	
	@Test
	fun `flag after positional treated as positional`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Positional("file", ""), required = false),
				Syntax.Leaf(Param.Flag("verbose", ""), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
				Syntax.Leaf(Param.Flag("must", ""), required = true),
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
				Syntax.Leaf(Param.Flag("must", ""), required = true),
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
							Syntax.Leaf(Param.Flag("list", ""), required = true),
							Syntax.Leaf(Param.Value("count", ""), required = false),
						),
						Syntax.all(
							Syntax.Leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.Leaf(Param.Flag("key", ""), required = false),
								Syntax.Leaf(Param.Flag("value", ""), required = false),
								Syntax.Leaf(Param.Flag("desc", ""), required = false),
								required = false,
							),
						),
					),
					Syntax.Leaf(Param.Flag("full", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("put", ""), required = true),
					Syntax.Leaf(Param.Positional("value", ""), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
							Syntax.Leaf(Param.Flag("list", ""), required = true),
							Syntax.Leaf(Param.Value("count", ""), required = false),
						),
						Syntax.all(
							Syntax.Leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.Leaf(Param.Flag("key", ""), required = false),
								Syntax.Leaf(Param.Flag("value", ""), required = false),
								Syntax.Leaf(Param.Flag("desc", ""), required = false),
								required = false,
							),
						),
					),
					Syntax.Leaf(Param.Flag("full", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("put", ""), required = true),
					Syntax.Leaf(Param.Positional("value", ""), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
							Syntax.Leaf(Param.Flag("list", ""), required = true),
							Syntax.Leaf(Param.Value("count", ""), required = false),
						),
						Syntax.all(
							Syntax.Leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.Leaf(Param.Flag("key", ""), required = false),
								Syntax.Leaf(Param.Flag("value", ""), required = false),
								Syntax.Leaf(Param.Flag("desc", ""), required = false),
								required = false,
							),
						),
					),
					Syntax.Leaf(Param.Flag("full", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("put", ""), required = true),
					Syntax.Leaf(Param.Positional("value", ""), required = false),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
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
							Syntax.Leaf(Param.Flag("list", ""), required = true),
							Syntax.Leaf(Param.Value("count", ""), required = false),
						),
						Syntax.all(
							Syntax.Leaf(Param.Flag("search", ""), required = true),
							Syntax.xor(
								Syntax.Leaf(Param.Flag("key", ""), required = false),
								Syntax.Leaf(Param.Flag("value", ""), required = false),
								Syntax.Leaf(Param.Flag("desc", ""), required = false),
								required = false,
							),
						),
					),
					Syntax.Leaf(Param.Flag("full", ""), required = false),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("put", ""), required = true),
					Syntax.Leaf(Param.Positional("value", ""), required = false),
				),
			)
		)
		assertEquals(1, dispatch("test", "--search", "--key", "--value").done().exitCode)
	}
	
	// ── xor inside all with positionals ──────────────────────────
	@Test
	fun `xor with positionals in all group picks flag branch`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("list", ""), required = false),
				Syntax.all(
					Syntax.xor(
						Syntax.Leaf(Param.Flag("remove", "", listOf("rm")), required = false),
						Syntax.Leaf(Param.Flag("set-default", ""), required = false),
					),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--set-default", "prov", "mdl")
		assertTrue(captured!!.has("set-default"))
		assertEquals(listOf("prov", "mdl"), captured.positional)
	}
	
	@Test
	fun `xor with positionals in all group picks remove branch`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("list", ""), required = false),
				Syntax.all(
					Syntax.xor(
						Syntax.Leaf(Param.Flag("remove", "", listOf("rm")), required = false),
						Syntax.Leaf(Param.Flag("set-default", ""), required = false),
					),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--rm", "prov", "mdl")
		assertTrue(captured!!.has("remove"))
		assertEquals(listOf("prov", "mdl"), captured.positional)
	}
	
	@Test
	fun `xor with positionals missing args fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("list", ""), required = false),
				Syntax.all(
					Syntax.xor(
						Syntax.Leaf(Param.Flag("remove", ""), required = false),
						Syntax.Leaf(Param.Flag("set-default", ""), required = false),
					),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		)
		// only one positional, need two
		assertEquals(1, dispatch("test", "--set-default", "prov").done().exitCode)
	}
	
	@Test
	fun `xor with positionals too many args fails`() {
		simpleCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("list", ""), required = false),
				Syntax.all(
					Syntax.xor(
						Syntax.Leaf(Param.Flag("remove", ""), required = false),
						Syntax.Leaf(Param.Flag("set-default", ""), required = false),
					),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		)
		assertEquals(1, dispatch("test", "--rm", "a", "b", "c").done().exitCode)
	}
	
	@Test
	fun `xor with positionals no flag also works`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.Leaf(Param.Flag("list", ""), required = false),
				Syntax.all(
					Syntax.xor(
						Syntax.Leaf(Param.Flag("remove", ""), required = false),
						Syntax.Leaf(Param.Flag("set-default", ""), required = false),
					),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		// remove with short alias
		dispatch("test", "-r", "prov", "mdl")
		assertTrue(captured!!.has("remove"))
		assertEquals(listOf("prov", "mdl"), captured.positional)
	}
	
	@Test
	fun `positional in xor branch selected by position count`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.xor(
				Syntax.all(
					Syntax.Leaf(Param.Flag("add", ""), required = false),
					Syntax.Leaf(Param.Positional("name", ""), required = true),
				),
				Syntax.all(
					Syntax.Leaf(Param.Flag("remove", ""), required = false),
					Syntax.Leaf(Param.Positional("provider", ""), required = true),
					Syntax.Leaf(Param.Positional("model", ""), required = true),
				),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		// two positionals selects the remove branch
		dispatch("test", "--remove", "prov", "mdl")
		assertTrue(captured!!.has("remove"))
		assertEquals(listOf("prov", "mdl"), captured.positional)
	}
	
	@Test
	fun `optional positional accepted`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Flag("verbose", ""), required = false),
				Syntax.Leaf(Param.Positional("file", ""), required = false),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test")
		assertTrue(captured != null)
		assertTrue(captured.positional.isEmpty())
	}
	
	@Test
	fun `value param and positional coexist`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Value("count", ""), required = false),
				Syntax.Leaf(Param.Positional("file", ""), required = true),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--count", "5", "data.txt")
		assertEquals("5", captured!!.get("count"))
		assertEquals(listOf("data.txt"), captured.positional)
	}
	
	@Test
	fun `value param after positional treated as positional`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.Leaf(Param.Value("output", ""), required = false),
				Syntax.Leaf(Param.Positional("file", ""), required = true),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		// "data.txt" is positional, "--output" is still parsed as named
		dispatch("test", "data.txt", "--output", "out.txt")
		assertEquals(listOf("data.txt"), captured!!.positional)
		assertEquals("out.txt", captured.get("output"))
	}
	
	@Test
	fun `all with xor and single positional`() {
		var captured: Request? = null
		registerCommand(
			"test", Syntax.all(
				Syntax.xor(
					Syntax.Leaf(Param.Flag("get", ""), required = false),
					Syntax.Leaf(Param.Flag("set", ""), required = false),
				),
				Syntax.Leaf(Param.Positional("key", ""), required = true),
			)
		) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--set", "mykey")
		assertTrue(captured!!.has("set"))
		assertEquals(listOf("mykey"), captured.positional)
	}
}
