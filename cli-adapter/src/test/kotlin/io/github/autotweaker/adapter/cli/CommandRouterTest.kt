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

import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.console.CmdOutput
import io.github.autotweaker.adapter.cli.syntax.Param
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.api.ServiceRegistry
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.initServices
import io.github.autotweaker.api.types.config.SettingValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterTest {
	
	companion object {
		private val settingService = mockk<SettingService>(relaxed = true)
		
		init {
			every { settingService.get<SettingValue.ValInt, Int>(any()) } returns 100_000
			initServices(
				ServiceRegistry(
					mockk(relaxed = true),
					mockk(relaxed = true),
					{ mockk(relaxed = true) },
					{ settingService },
					{ mockk(relaxed = true) }
				)
			)
		}
	}
	
	private val core = mockk<CoreAPI>(relaxed = true)
	private val commands = mutableListOf<Command>()
	private lateinit var router: CommandRouter
	
	@BeforeTest
	fun setUp() {
		commands.clear()
		val secret = mockk<CoreAPI.SecretAPI>()
		every { core.secret } returns secret
		every { secret.isUnlocked } returns MutableStateFlow(true)
		router = CommandRouter(core, commands)
	}
	
	private fun registerCommand(
		name: String,
		syntax: Syntax,
		onExecute: suspend Console.(CoreAPI) -> Nothing = { done() },
	): Command {
		val cmd = object : Command {
			override val name = name
			override val description = ""
			override val syntax = syntax
			override suspend fun Console.execute(core: CoreAPI): Nothing = onExecute(core)
		}
		commands.add(cmd)
		router = CommandRouter(core, commands)
		return cmd
	}
	
	private fun dispatch(vararg args: String): Pair<Int, List<CmdOutput>> = runBlocking {
		val outputs = mutableListOf<CmdOutput>()
		val exitCode = router.dispatch(
			request = CliMessage.Command(args = args.toList(), prog = "at", isTty = false),
			requestId = "test",
			stdin = Channel(),
			prompt = { "" },
			output = { outputs.add(it) },
		)
		exitCode to outputs
	}
	
	private fun List<CmdOutput>.stderr(): List<String> =
		filter { it.channel == OutputChannel.STDERR }.map { it.text }
	
	private fun all(vararg children: Syntax, required: Boolean = true) = Syntax.All(children.toList(), required)
	
	// ── routing ───────────────────────────────────────────────────
	
	@Test
	fun emptyCommandShowsCopyright() {
		val (exitCode, outputs) = dispatch()
		assertEquals(0, exitCode)
		assertTrue(outputs.any { it.text.contains("AutoTweaker") })
	}
	
	@Test
	fun unknownCommandReturnsError() {
		val (exitCode, outputs) = dispatch("nonexistent")
		assertEquals(1, exitCode)
		assertTrue(outputs.stderr().isNotEmpty())
	}
	
	@Test
	fun knownCommandDispatched() {
		registerCommand("test", Syntax.EMPTY)
		assertEquals(0, dispatch("test").first)
	}
	
	@Test
	fun argsForwardedToHandler() {
		var captured: Boolean? = null
		registerCommand(
			"test",
			all(Syntax.Leaf(Param.Flag("verbose", "v", listOf("v")), required = false))
		) {
			captured = hasArg("verbose")
			done()
		}
		dispatch("test", "--verbose")
		assertTrue(captured!!)
	}
	
	// ── keystore lock ─────────────────────────────────────────────
	
	@Test
	fun lockedKeystoreRejectsCommand() {
		every { core.secret.isUnlocked } returns MutableStateFlow(false)
		registerCommand("test", Syntax.EMPTY)
		assertEquals(1, dispatch("test").first)
	}
	
	@Test
	fun helpAllowedWhenLocked() {
		every { core.secret.isUnlocked } returns MutableStateFlow(false)
		// help is auto-registered, dispatch should not fail with keystore error
		assertEquals(0, dispatch("help").first)
	}
	
	// ── syntax conflict ───────────────────────────────────────────
	
	@Test
	fun syntaxConflictDetected() {
		val syntax = all(
			Syntax.Leaf(Param.Flag("same", "a", listOf("s")), required = false),
			Syntax.Leaf(Param.Flag("same", "b", listOf("s")), required = false),
		)
		registerCommand("test", syntax)
		assertEquals(1, dispatch("test").first)
		assertTrue(dispatch("test").second.stderr().any { it.contains("Duplicate") || it.contains("conflict") })
	}
	
	// ── invalid args ──────────────────────────────────────────────
	
	@Test
	fun invalidArgsReturnsError() {
		registerCommand("test", all(Syntax.Leaf(Param.Flag("verbose", "v", listOf("v")), required = true)))
		assertEquals(1, dispatch("test").first)
	}
}
