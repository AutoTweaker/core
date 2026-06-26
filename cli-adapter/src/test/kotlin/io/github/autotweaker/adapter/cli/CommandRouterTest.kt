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

import io.github.autotweaker.api.ServiceRegistry
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.initServices

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
	
	companion object {
		private val settingService = mockk<SettingService>(relaxed = true)
		
		init {
			every { settingService.get<SettingValue.ValInt>(any()) } returns SettingValue.ValInt(100_000)
			initServices(
				ServiceRegistry(
					mockk(relaxed = true),
					mockk(relaxed = true),
					settingService,
					mockk(relaxed = true)
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
		handle: (Request) -> List<CmdOutput> = { listOf(CmdOutput.Done(0)) },
	): Command {
		val cmd = mockk<Command>()
		every { cmd.name } returns name
		every { cmd.description } returns ""
		every { cmd.syntax } returns syntax
		every { cmd.init(any<CoreAPI>()) } returns Unit
		every { cmd.handle(any(), any()) } answers {
			val request = firstArg<Request>()
			flowOf(*(handle(request).toTypedArray()))
		}
		commands.add(cmd)
		router = CommandRouter(core, commands)
		return cmd
	}
	
	private fun dispatch(vararg args: String): List<CmdOutput> = runBlocking {
		router.dispatch(CliMessage.Command(args = args.toList())) { _, _ -> "" }.toList()
	}
	
	private fun List<CmdOutput>.done(): CmdOutput.Done = last() as CmdOutput.Done
	private fun List<CmdOutput>.stderr(): List<String> =
		filterIsInstance<CmdOutput.Data>().filter { it.channel == CmdOutput.Channel.STDERR }.map { it.text }
	
	// ── routing ───────────────────────────────────────────────────
	
	@Test
	fun emptyCommandShowsCopyright() {
		val r = dispatch()
		assertEquals(0, r.done().exitCode)
		assertTrue(r.any { it is CmdOutput.Data && it.text.contains("AutoTweaker") })
	}
	
	@Test
	fun unknownCommandReturnsError() {
		val r = dispatch("nonexistent")
		assertEquals(1, r.done().exitCode)
		assertTrue(r.stderr().isNotEmpty())
	}
	
	@Test
	fun knownCommandDispatched() {
		registerCommand("test", Syntax.none())
		assertEquals(0, dispatch("test").done().exitCode)
	}
	
	@Test
	fun argsForwardedToHandler() {
		var captured: Request? = null
		registerCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "v"), required = false))) {
			captured = it; listOf(CmdOutput.Done(0))
		}
		dispatch("test", "--verbose")
		assertTrue(captured!!.has("verbose"))
	}
	
	// ── keystore lock ─────────────────────────────────────────────
	
	@Test
	fun lockedKeystoreRejectsCommand() {
		every { core.secret.isUnlocked } returns MutableStateFlow(false)
		registerCommand("test", Syntax.none())
		assertEquals(1, dispatch("test").done().exitCode)
	}
	
	@Test
	fun helpAllowedWhenLocked() {
		every { core.secret.isUnlocked } returns MutableStateFlow(false)
		// help is auto-registered, dispatch should not fail with keystore error
		val r = dispatch("help")
		assertEquals(0, r.done().exitCode)
	}
	
	// ── syntax conflict ───────────────────────────────────────────
	
	@Test
	fun syntaxConflictDetected() {
		val syntax = Syntax.all(
			Syntax.Leaf(Param.Flag("same", "a", listOf("s")), required = false),
			Syntax.Leaf(Param.Flag("same", "b", listOf("s")), required = false),
		)
		registerCommand("test", syntax)
		assertEquals(1, dispatch("test").done().exitCode)
		assertTrue(dispatch("test").stderr().any { it.contains("Duplicate") || it.contains("conflict") })
	}
	
	// ── invalid args ──────────────────────────────────────────────
	
	@Test
	fun invalidArgsReturnsError() {
		registerCommand("test", Syntax.all(Syntax.Leaf(Param.Flag("verbose", "v"), required = true)))
		assertEquals(1, dispatch("test").done().exitCode)
	}
}
