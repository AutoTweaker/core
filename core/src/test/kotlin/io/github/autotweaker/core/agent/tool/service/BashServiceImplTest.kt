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

package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.container.CommandResult
import io.github.autotweaker.core.container.ContainerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class BashServiceImplTest {
	
	private lateinit var tmpDir: Path
	
	@BeforeTest
	fun setUp() {
		tmpDir = Files.createTempDirectory("bash-test")
	}
	
	@AfterTest
	fun tearDown() {
		tmpDir.toFile().deleteRecursively()
	}
	
	@Test
	fun `run local echo command succeeds`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("echo hello", timeoutSeconds = 10, emptyMap())
		
		assertEquals(0, result.exitCode)
		assertEquals("hello\n", result.stdout)
		assertEquals("", result.stderr)
		assertFalse(result.timeout)
		assertTrue(result.durationSeconds > 0)
	}
	
	@Test
	fun `run local failed command returns non zero exit`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("false", timeoutSeconds = 10, emptyMap())
		
		assertEquals(1, result.exitCode)
		assertFalse(result.timeout)
	}
	
	@Test
	fun `run local timeout`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("sleep 100", timeoutSeconds = 1, emptyMap())
		
		assertEquals(-1, result.exitCode)
		assertTrue(result.timeout)
	}
	
	@Test
	fun `run local with environment variables`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("echo \$MY_VAR", timeoutSeconds = 10, mapOf("MY_VAR" to "test_value"))
		
		assertEquals(0, result.exitCode)
		assertEquals("test_value\n", result.stdout)
	}
	
	@Test
	fun `run local stderr capture`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("echo error >&2", timeoutSeconds = 10, emptyMap())
		
		assertEquals(0, result.exitCode)
		assertEquals("error\n", result.stderr)
	}
	
	@Test
	fun `run local complex command`() = runTest {
		val service = BashServiceImpl(tmpDir, inContainer = false, containerWorkDir = tmpDir)
		val result = service.run("echo start && echo end", timeoutSeconds = 10, emptyMap())
		
		assertEquals(0, result.exitCode)
		assertTrue(result.stdout.contains("start"))
		assertTrue(result.stdout.contains("end"))
	}
	
	@Test
	fun `run in container delegates to ContainerManager`() = runTest {
		mockkObject(ContainerManager)
		coEvery { ContainerManager.exec(any(), any(), any()) } returns CommandResult(0, "container out", "")
		
		val service = BashServiceImpl(tmpDir, inContainer = true, containerWorkDir = tmpDir)
		val result = service.run("echo hello", timeoutSeconds = 10, emptyMap())
		
		assertEquals(0, result.exitCode)
		assertEquals("container out", result.stdout)
		assertFalse(result.timeout)
		
		unmockkObject(ContainerManager)
	}
	
	@Test
	fun `run in container timeout exit code 124`() = runTest {
		mockkObject(ContainerManager)
		coEvery { ContainerManager.exec(any(), any(), any()) } returns CommandResult(124, "", "timeout")
		
		val service = BashServiceImpl(tmpDir, inContainer = true, containerWorkDir = tmpDir)
		val result = service.run("sleep 100", timeoutSeconds = 5, emptyMap())
		
		assertEquals(124, result.exitCode)
		assertTrue(result.timeout)
		
		unmockkObject(ContainerManager)
	}
	
	@Test
	fun `run in container with env vars`() = runTest {
		mockkObject(ContainerManager)
		coEvery { ContainerManager.exec(any(), any(), any()) } returns CommandResult(0, "ok", "")
		
		val service = BashServiceImpl(tmpDir, inContainer = true, containerWorkDir = tmpDir)
		val result = service.run("echo test", timeoutSeconds = 10, mapOf("KEY" to "VAL"))
		
		assertEquals(0, result.exitCode)
		coVerify { ContainerManager.exec("bash", "-lc", match { it.contains("KEY") && it.contains("VAL") }) }
		
		unmockkObject(ContainerManager)
	}
}
