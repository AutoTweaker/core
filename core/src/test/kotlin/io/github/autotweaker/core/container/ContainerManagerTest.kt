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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ContainerManagerTest {
	
	private val service = mockk<ContainerService>(relaxUnitFun = true)
	
	@BeforeTest
	fun setUp() {
		resetState()
		coEvery { service.start(any(), any()) } returns "container-123"
		coEvery { service.exec(any(), any(), any(), any()) } returns CommandResult(0, "", "")
	}
	
	@AfterTest
	fun tearDown() {
		resetState()
	}
	
	// region start
	
	@Test
	fun `start returns container id from service`() = runTest {
		val id = ContainerManager.start(service, "ubuntu:latest")
		assertEquals("container-123", id)
		coVerify { service.start("ubuntu:latest", any()) }
	}
	
	@Test
	fun `start sets isRunning to true`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		assertTrue(ContainerManager.isRunning)
	}
	
	@Test
	fun `start sets containerId to returned id`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		assertEquals("container-123", ContainerManager.containerId)
	}
	
	@Test
	fun `start passes ContainerConfig to service`() = runTest {
		val config = ContainerConfig(name = "test", env = mapOf("A" to "1"))
		ContainerManager.start(service, "ubuntu:latest", config)
		coVerify { service.start("ubuntu:latest", config) }
	}
	
	@Test
	fun `start throws ContainerAlreadyRunningException when already running`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		val ex = assertFailsWith<ContainerAlreadyRunningException> {
			ContainerManager.start(service, "another:image")
		}
		assertTrue(ex.message!!.contains("container-123"))
	}
	
	@Test
	fun `start does not set containerId when service start throws`() = runTest {
		coEvery { service.start(any(), any()) } throws RuntimeException("start failed")
		assertFailsWith<RuntimeException> {
			ContainerManager.start(service, "ubuntu:latest")
		}
		assertFalse(ContainerManager.isRunning)
		assertNull(ContainerManager.containerId)
	}
	
	// endregion
	
	// region stop
	
	@Test
	fun `stop calls service stop with correct container id`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		ContainerManager.stop()
		coVerify { service.stop("container-123") }
	}
	
	@Test
	fun `stop clears isRunning and containerId`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		ContainerManager.stop()
		assertFalse(ContainerManager.isRunning)
		assertNull(ContainerManager.containerId)
	}
	
	@Test
	fun `stop silently returns when no container running`() = runTest {
		ContainerManager.stop()
		coVerify(exactly = 0) { service.stop(any()) }
	}
	
	@Test
	fun `stop silently returns when service is null but containerId is set`() = runTest {
		setInternalState(containerId = "orphan-123")
		ContainerManager.stop()
		coVerify(exactly = 0) { service.stop(any()) }
	}
	
	@Test
	fun `stop clears state even when service stop throws`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		coEvery { service.stop(any()) } throws RuntimeException("stop failed")
		assertFailsWith<RuntimeException> {
			ContainerManager.stop()
		}
		assertFalse(ContainerManager.isRunning)
		assertNull(ContainerManager.containerId)
	}
	
	// endregion
	
	// region exec
	
	@Test
	fun `exec delegates to service with correct parameters`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		ContainerManager.exec("ls", "-la")
		coVerify { service.exec("container-123", listOf("ls", "-la"), null, 30) }
	}
	
	@Test
	fun `exec returns CommandResult from service`() = runTest {
		val expected = CommandResult(0, "stdout", "stderr")
		coEvery { service.exec(any(), any(), any(), any()) } returns expected
		ContainerManager.start(service, "ubuntu:latest")
		val result = ContainerManager.exec("echo", "hello")
		assertEquals(expected, result)
	}
	
	@Test
	fun `exec throws NoContainerRunningException when no container`() = runTest {
		assertFailsWith<NoContainerRunningException> {
			ContainerManager.exec("ls")
		}
	}
	
	@Test
	fun `exec throws NoContainerRunningException when service is null`() = runTest {
		setInternalState(containerId = "orphan-123")
		assertFailsWith<NoContainerRunningException> {
			ContainerManager.exec("ls")
		}
	}
	
	// endregion
	
	// region execShell
	
	@Test
	fun `execShell wraps command in bash -c`() = runTest {
		ContainerManager.start(service, "ubuntu:latest")
		ContainerManager.execShell("echo hello")
		coVerify { service.exec("container-123", listOf("bash", "-c", "echo hello"), null, 30) }
	}
	
	@Test
	fun `execShell throws NoContainerRunningException when no container`() = runTest {
		assertFailsWith<NoContainerRunningException> {
			ContainerManager.execShell("echo hello")
		}
	}
	
	// endregion
	
	// region isRunning / containerId
	
	@Test
	fun `isRunning returns false initially`() {
		assertFalse(ContainerManager.isRunning)
	}
	
	@Test
	fun `containerId returns null initially`() {
		assertNull(ContainerManager.containerId)
	}
	
	// endregion
	
	companion object {
		private fun resetState() {
			setInternalState(containerId = null, service = null)
		}
		
		private fun setInternalState(containerId: String?, service: ContainerService? = null) {
			val containerIdField = ContainerManager::class.java.getDeclaredField("_containerId")
			containerIdField.isAccessible = true
			containerIdField.set(ContainerManager, containerId)
			
			val serviceField = ContainerManager::class.java.getDeclaredField("_service")
			serviceField.isAccessible = true
			serviceField.set(ContainerManager, service)
		}
	}
}
