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

package io.github.autotweaker.core.container

import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import kotlin.test.*

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
		assertEquals("autotweaker", config.name)
		assertTrue(config.env.isEmpty())
		assertEquals(Paths.get("/workspace"), config.workDir)
		assertEquals(Paths.get("~/.config/autotweaker/container/workspace"), config.workspaceHostPath)
	}
	
	@Test
	fun `custom values override defaults`() {
		val config = ContainerConfig(
			name = "custom",
			env = mapOf("KEY" to "VALUE"),
			workDir = Paths.get("/tmp/work"),
			workspaceHostPath = Paths.get("/tmp/host"),
		)
		assertEquals("custom", config.name)
		assertEquals(mapOf("KEY" to "VALUE"), config.env)
		assertEquals(Paths.get("/tmp/work"), config.workDir)
		assertEquals(Paths.get("/tmp/host"), config.workspaceHostPath)
	}
}

class ContainerServiceTest {
	
	private suspend fun callExecWithDefaults(service: ContainerService): CommandResult {
		return service.exec("id", listOf("cmd"))
	}
	
	@Test
	fun `exec with default workDir and timeout`() = runTest {
		val impl = object : ContainerService {
			override suspend fun start(image: String, config: ContainerConfig) = "test"
			override suspend fun stop(containerId: String) {}
			override suspend fun exec(
				containerId: String,
				command: List<String>,
				workDir: String?,
				timeoutSeconds: Long,
			) = CommandResult(0, workDir ?: "default", timeoutSeconds.toString())
		}
		
		val result = callExecWithDefaults(impl)
		assertEquals("default", result.stdout)
		assertEquals("30", result.stderr)
	}
}
