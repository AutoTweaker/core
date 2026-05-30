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

package io.github.autotweaker.core.infrastructure.container

import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.*
import kotlin.time.Duration

class ContainerExceptionsTest {
	
	@Test
	fun `ContainerAlreadyRunningException contains container id in message`() {
		val ex = ContainerAlreadyRunningException("abc123")
		assertTrue(ex.message!!.contains("abc123"))
		assertTrue(ex.message!!.contains("already running"))
		assertIs<IllegalStateException>(ex)
	}
	
	@Test
	fun `NoContainerRunningException has descriptive message`() {
		val ex = NoContainerRunningException()
		assertTrue(ex.message!!.contains("No container is running"))
		assertIs<IllegalStateException>(ex)
	}
	
	@Test
	fun `ContainerOperationException with message only`() {
		val ex = ContainerOperationException("something went wrong")
		assertEquals("something went wrong", ex.message)
		assertNull(ex.cause)
		assertIs<RuntimeException>(ex)
	}
	
	@Test
	fun `ContainerOperationException with message and cause`() {
		val cause = RuntimeException("root cause")
		val ex = ContainerOperationException("failed", cause)
		assertEquals("failed", ex.message)
		assertSame(cause, ex.cause)
	}
}

class CommandResultTest {
	
	@Test
	fun `properties are set correctly`() {
		val result = CommandResult(0, "stdout text", "stderr text")
		assertEquals(0, result.exitCode)
		assertEquals("stdout text", result.stdout)
		assertEquals("stderr text", result.stderr)
	}
	
	@Test
	fun `equality works by value`() {
		val a = CommandResult(1, "out", "err")
		val b = CommandResult(1, "out", "err")
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
	}
	
	@Test
	fun `copy creates independent instance`() {
		val original = CommandResult(0, "a", "b")
		val copied = original.copy(exitCode = 1)
		assertEquals(0, original.exitCode)
		assertEquals(1, copied.exitCode)
	}
}

class ContainerConfigTest {
	
	@Test
	fun `default values are correct`() {
		val config = ContainerConfig()
		assertEquals("autotweaker-workspace", config.name)
		assertTrue(config.env.isEmpty())
		assertEquals(Path.of("/workspace"), config.workDir)
		assertEquals(
			Path.of(System.getProperty("user.home"), ".config", "autotweaker", "container", "workspace"),
			config.workspaceHostPath
		)
	}
	
	@Test
	fun `custom values override defaults`() {
		val config = ContainerConfig(
			name = "custom",
			env = mapOf("KEY" to "VALUE"),
			workDir = Path.of("/tmp/work"),
			workspaceHostPath = Path.of("/tmp/host"),
		)
		assertEquals("custom", config.name)
		assertEquals(mapOf("KEY" to "VALUE"), config.env)
		assertEquals(Path.of("/tmp/work"), config.workDir)
		assertEquals(Path.of("/tmp/host"), config.workspaceHostPath)
	}
}

class ContainerServiceTest {
	
	@Test
	fun `execStream is called with correct parameters`() = runTest {
		var capturedCommand: List<String>? = null
		val impl = object : ContainerService {
			override suspend fun pullImage(image: String) {}
			override suspend fun start(image: String, config: ContainerConfig) = "test"
			override suspend fun stop(containerId: String) {}
			override fun execStream(
				containerId: String, command: List<String>, workDir: Path?, timeout: Duration, env: Map<String, String>,
			): Flow<ShellEvent> {
				capturedCommand = command
				return flowOf(ShellEvent.Exit(ShellResult(0, false, Duration.ZERO)))
			}
		}
		
		impl.execStream("id", listOf("echo", "hello"), timeout = Duration.parse("30s")).collect {}
		assertEquals(listOf("echo", "hello"), capturedCommand)
	}
}
