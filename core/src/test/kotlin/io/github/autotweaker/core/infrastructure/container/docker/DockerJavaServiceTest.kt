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

package io.github.autotweaker.core.infrastructure.container.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.core.DockerClientImpl
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.container.ContainerOperationException
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

class DockerJavaServiceTest {
	
	private fun createFrame(streamType: StreamType, text: String): Frame {
		val frame = mockk<Frame>()
		every { frame.streamType } returns streamType
		every { frame.payload } returns text.toByteArray()
		return frame
	}
	
	@Suppress("DEPRECATION")
	private fun setupClient(): DockerClient {
		val client = mockk<DockerClientImpl>(relaxed = true)
		mockkStatic(DockerClientImpl::class)
		every { DockerClientImpl.getInstance() } returns client
		mockkStatic(Files::class)
		every { Files.createDirectories(any()) } returns mockk()

		return client
	}
	
	// region start
	
	@Test
	fun `start returns container id`() = runTest {
		val client = setupClient()
		
		val createResponse = mockk<CreateContainerResponse>()
		every { createResponse.id } returns "container-123"
		
		val createCmd = mockk<CreateContainerCmd>(relaxed = true)
		every { createCmd.withName(any()) } returns createCmd
		every { createCmd.withWorkingDir(any()) } returns createCmd
		every { createCmd.withEnv(any<List<String>>()) } returns createCmd
		every { createCmd.withHostConfig(any()) } returns createCmd
		every { createCmd.withEntrypoint(any<String>(), any<String>(), any<String>()) } returns createCmd
		every { createCmd.exec() } returns createResponse
		every { client.createContainerCmd(any()) } returns createCmd
		
		val service = DockerJavaService()
		val id = service.start("ubuntu:latest", ContainerConfig())
		
		assertEquals("container-123", id)
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `start throws ContainerOperationException on ConflictException`() = runTest {
		val client = setupClient()
		every { client.createContainerCmd(any()) } throws ConflictException("name already in use")
		
		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.start("ubuntu:latest", ContainerConfig(name = "test"))
		}
		assertTrue(ex.message!!.contains("already exists"))
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `start throws ContainerOperationException on NotFoundException`() = runTest {
		val client = setupClient()
		every { client.createContainerCmd(any()) } throws NotFoundException("image not found")

		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.start("nonexistent:latest", ContainerConfig())
		}
		assertTrue(ex.message!!.contains("not found"))

		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}

	@Test
	fun `start throws ContainerOperationException on generic exception`() = runTest {
		val client = setupClient()
		every { client.createContainerCmd(any()) } throws RuntimeException("network error")

		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.start("ubuntu:latest", ContainerConfig())
		}
		assertTrue(ex.message!!.contains("Failed to start container"))
		assertTrue(ex.message!!.contains("network error"))

		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `start passes config values to create container command`() = runTest {
		val client = setupClient()
		
		val createResponse = mockk<CreateContainerResponse>()
		every { createResponse.id } returns "container-456"
		
		val createCmd = mockk<CreateContainerCmd>(relaxed = true)
		every { createCmd.withName("my-container") } returns createCmd
		every { createCmd.withWorkingDir("/workspace") } returns createCmd
		every { createCmd.withEnv(listOf("KEY=VALUE")) } returns createCmd
		every { createCmd.withHostConfig(any()) } returns createCmd
		every { createCmd.withEntrypoint("tail", "-f", "/dev/null") } returns createCmd
		every { createCmd.exec() } returns createResponse
		every { client.createContainerCmd("ubuntu:latest") } returns createCmd
		
		val service = DockerJavaService()
		val config = ContainerConfig(
			name = "my-container",
			env = mapOf("KEY" to "VALUE"),
		)
		val id = service.start("ubuntu:latest", config)
		
		assertEquals("container-456", id)
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	// endregion
	
	// region stop
	
	@Test
	fun `stop calls stop and remove on client`() = runTest {
		setupClient()
		
		val service = DockerJavaService()
		service.stop("container-123")
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `stop silently ignores NotFoundException`() = runTest {
		val client = setupClient()
		every { client.stopContainerCmd(any()) } throws NotFoundException("not found")
		
		val service = DockerJavaService()
		service.stop("container-gone")
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `stop throws ContainerOperationException on generic exception`() = runTest {
		val client = setupClient()
		every { client.stopContainerCmd(any()) } throws RuntimeException("stop failed")
		
		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.stop("container-123")
		}
		assertTrue(ex.message!!.contains("Failed to stop container"))
		assertTrue(ex.message!!.contains("stop failed"))
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	// endregion
	
	// region execStream
	
	@Test
	fun `execStream emits stdout stderr and exit events`() = runTest {
		val client = setupClient()
		
		val execCreateResponse = mockk<ExecCreateCmdResponse>()
		every { execCreateResponse.id } returns "exec-789"
		
		val execCreateCmd = mockk<ExecCreateCmd>(relaxed = true)
		every { execCreateCmd.withCmd(*anyVararg()) } returns execCreateCmd
		every { execCreateCmd.withAttachStdout(any()) } returns execCreateCmd
		every { execCreateCmd.withAttachStderr(any()) } returns execCreateCmd
		every { execCreateCmd.withEnv(any()) } returns execCreateCmd
		every { execCreateCmd.exec() } returns execCreateResponse
		every { client.execCreateCmd(any()) } returns execCreateCmd
		
		val callbackSlot = slot<ResultCallback<Frame>>()
		val execStartCmd = mockk<ExecStartCmd>()
		every { execStartCmd.exec(capture(callbackSlot)) } answers {
			callbackSlot.captured.onNext(createFrame(StreamType.STDOUT, "hello"))
			callbackSlot.captured.onNext(createFrame(StreamType.STDERR, "error"))
			callbackSlot.captured.onComplete()
			callbackSlot.captured
		}
		every { client.execStartCmd("exec-789") } returns execStartCmd
		
		val inspectResponse = mockk<InspectExecResponse>()
		every { inspectResponse.exitCodeLong } returns 0L
		val inspectCmd = mockk<InspectExecCmd>()
		every { inspectCmd.exec() } returns inspectResponse
		every { client.inspectExecCmd("exec-789") } returns inspectCmd
		
		val service = DockerJavaService()
		val events =
			service.execStream("container-123", listOf("echo", "hello"), timeout = Duration.parse("30s")).toList()
		
		assertEquals(3, events.size)
		assertEquals("hello", (events[0] as ShellEvent.Stdout).text)
		assertEquals("error", (events[1] as ShellEvent.Stderr).text)
		val exit = events[2] as ShellEvent.Exit
		assertEquals(0, exit.result.exitCode)
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `execStream passes workDir to exec create command`() = runTest {
		val client = setupClient()
		
		val execCreateResponse = mockk<ExecCreateCmdResponse>()
		every { execCreateResponse.id } returns "exec-1"
		
		val execCreateCmd = mockk<ExecCreateCmd>(relaxed = true)
		every { execCreateCmd.withCmd(*anyVararg()) } returns execCreateCmd
		every { execCreateCmd.withAttachStdout(any()) } returns execCreateCmd
		every { execCreateCmd.withAttachStderr(any()) } returns execCreateCmd
		every { execCreateCmd.withEnv(any()) } returns execCreateCmd
		every { execCreateCmd.withWorkingDir("/tmp/work") } returns execCreateCmd
		every { execCreateCmd.exec() } returns execCreateResponse
		every { client.execCreateCmd(any()) } returns execCreateCmd
		
		val execStartCmd = mockk<ExecStartCmd>()
		every { execStartCmd.exec(any<ResultCallback<Frame>>()) } answers {
			val cb = firstArg<ResultCallback<Frame>>()
			cb.onComplete()
			cb
		}
		every { client.execStartCmd(any()) } returns execStartCmd
		
		val inspectResponse = mockk<InspectExecResponse>()
		every { inspectResponse.exitCodeLong } returns 0L
		every { client.inspectExecCmd(any()) } returns mockk {
			every { exec() } returns inspectResponse
		}
		
		val service = DockerJavaService()
		val events = service.execStream(
			"container-123", listOf("pwd"), timeout = Duration.parse("30s"), workDir = Path.of("/tmp/work")
		).toList()
		
		val exit = events.last() as ShellEvent.Exit
		assertEquals(0, exit.result.exitCode)
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `execStream exitCode -1 when exitCodeLong is null`() = runTest {
		val client = setupClient()
		
		val execCreateResponse = mockk<ExecCreateCmdResponse>()
		every { execCreateResponse.id } returns "exec-null"
		
		val execCreateCmd = mockk<ExecCreateCmd>(relaxed = true)
		every { execCreateCmd.withCmd(*anyVararg()) } returns execCreateCmd
		every { execCreateCmd.withAttachStdout(any()) } returns execCreateCmd
		every { execCreateCmd.withAttachStderr(any()) } returns execCreateCmd
		every { execCreateCmd.withEnv(any()) } returns execCreateCmd
		every { execCreateCmd.exec() } returns execCreateResponse
		every { client.execCreateCmd(any()) } returns execCreateCmd
		
		val execStartCmd = mockk<ExecStartCmd>()
		every { execStartCmd.exec(any<ResultCallback<Frame>>()) } answers {
			val cb = firstArg<ResultCallback<Frame>>()
			cb.onComplete()
			cb
		}
		every { client.execStartCmd(any()) } returns execStartCmd
		
		val inspectResponse = mockk<InspectExecResponse>()
		every { inspectResponse.exitCodeLong } returns null
		every { client.inspectExecCmd(any()) } returns mockk {
			every { exec() } returns inspectResponse
		}
		
		val service = DockerJavaService()
		val events = service.execStream("container-123", listOf("cmd"), timeout = Duration.parse("30s")).toList()
		
		val exit = events.last() as ShellEvent.Exit
		assertEquals(-1, exit.result.exitCode)
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `execStream throws ContainerOperationException on NotFoundException`() = runTest {
		val client = setupClient()
		every { client.execCreateCmd(any()) } throws NotFoundException("no such container")
		
		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.execStream("container-gone", listOf("ls"), timeout = Duration.parse("30s")).collect {}
		}
		assertTrue(ex.message!!.contains("Container not found"))
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	@Test
	fun `execStream throws ContainerOperationException on generic exception`() = runTest {
		val client = setupClient()
		every { client.execCreateCmd(any()) } throws RuntimeException("exec error")
		
		val service = DockerJavaService()
		val ex = assertFailsWith<ContainerOperationException> {
			service.execStream("container-123", listOf("ls"), timeout = Duration.parse("30s")).collect {}
		}
		assertTrue(ex.message!!.contains("Failed to exec command"))
		assertTrue(ex.message!!.contains("exec error"))
		
		unmockkStatic(DockerClientImpl::class)
		unmockkStatic(Files::class)
	}
	
	// endregion
}
