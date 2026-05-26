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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class BashServiceImplTest {
	
	@Test
	fun `run constructs ShellExec and delegates to executor`() = runTest {
		val executor = mockk<ShellExecutor>(relaxed = true)
		every { executor.exec(any()) } returns flowOf(
			ShellEvent.Stdout("hello\n"),
			ShellEvent.Exit(ShellResult(0, false, 0.01.seconds)),
		)
		
		val service = BashServiceImpl(executor, Path.of("/ws"), inContainer = false)
		val result = mutableListOf<ShellEvent>()
		service.run("echo hello", 10.seconds, emptyMap()).collect { result.add(it) }
		
		assertEquals(2, result.size)
		assertIs<ShellEvent.Stdout>(result[0])
		assertEquals("hello\n", (result[0] as ShellEvent.Stdout).text)
		val exit = result[1] as ShellEvent.Exit
		assertEquals(0, exit.result.exitCode)
		assertFalse(exit.result.timeout)
	}
	
	@Test
	fun `run passes inContainer flag to ShellExec`() = runTest {
		val slot = slot<ShellExec>()
		val executor = mockk<ShellExecutor>(relaxed = true)
		every { executor.exec(capture(slot)) } returns flowOf(
			ShellEvent.Exit(ShellResult(0, false, 0.seconds)),
		)
		
		val service = BashServiceImpl(executor, Path.of("/ws"), inContainer = true)
		service.run("cmd", 30.seconds, mapOf("K" to "V")).collect {}
		
		assertTrue(slot.captured.container)
		assertEquals("/ws", slot.captured.directory.toString())
		assertEquals("cmd", slot.captured.command)
		assertEquals(mapOf("K" to "V"), slot.captured.environment)
		assertEquals(30.seconds, slot.captured.timeout)
	}
	
	@Test
	fun `run passes non-container environment variables`() = runTest {
		val slot = slot<ShellExec>()
		val executor = mockk<ShellExecutor>(relaxed = true)
		every { executor.exec(capture(slot)) } returns flowOf(
			ShellEvent.Exit(ShellResult(0, false, 0.seconds)),
		)
		
		val service = BashServiceImpl(executor, Path.of("/tmp"), inContainer = false)
		service.run("echo test", 5.seconds, mapOf("KEY" to "VAL")).collect {}
		
		assertFalse(slot.captured.container)
		assertEquals(mapOf("KEY" to "VAL"), slot.captured.environment)
	}
}
