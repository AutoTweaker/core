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

import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class BashServiceImplTest {
	
	@Test
	fun `run constructs ShellExec and delegates to executor`() = runTest {
		val executor = mockk<ShellExecutor>(relaxed = true)
		every { executor.exec(any()) } returns flowOf(
			ShellEvent.Stdout("hello\n"),
			ShellEvent.Exit(ShellResult(0, false, 0.01.seconds)),
		)
		
		val env = mockk<AgentEnvironment>()
		every { env.workspace } returns WorkspaceMeta("test", false, Path.of("/ws"))
		
		val service = BashServiceImpl(executor, env)
		val result = mutableListOf<ShellEvent>()
		service.run("echo hello", 10.seconds, emptyMap()).collect { result.add(it) }
		
		assertEquals(2, result.size)
		assertIs<ShellEvent.Stdout>(result[0])
		assertEquals("hello\n", (result[0] as ShellEvent.Stdout).text)
	}
}
