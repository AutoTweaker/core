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

import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.Truncated
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemServiceImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val hostPath = Path.of("/host/ws/file.txt")
	private val containerPath = Path.of("/workspace/file.txt")
	private val workspace: () -> Path = { Path.of("/host/ws") }
	private val sha = Sha256(ByteArray(32) { 1 })
	
	private fun service(
		inContainer: Boolean,
	): Pair<FileSystemServiceImpl, RawFileSystem> {
		val fs = mockk<RawFileSystem>()
		val resolver = mockk<PathResolver>()
		every { resolver.inContainer(any()) } returns inContainer
		every { resolver.toHostPath(containerPath) } returns hostPath
		every { resolver.toAbsolutePath(Path.of("/host/ws"), any()) } returns hostPath
		return FileSystemServiceImpl(fs, resolver, workspace) to fs
	}
	
	@Test
	fun `normalize resolves through path resolver`() {
		val (service, _) = service(inContainer = false)
		
		assertEquals(hostPath, service.normalize("file.txt"))
	}
	
	@Test
	fun `container path converted to host before delegation`() = runTest {
		val (service, fs) = service(inContainer = true)
		coEvery { fs.exists(hostPath) } returns true
		
		assertTrue(service.exists(containerPath))
		coVerify { fs.exists(hostPath) }
	}
	
	@Test
	fun `host path passed through unchanged`() = runTest {
		val (service, fs) = service(inContainer = false)
		coEvery { fs.exists(hostPath) } returns true
		
		assertTrue(service.exists(hostPath))
		coVerify { fs.exists(hostPath) }
	}
	
	@Test
	fun `isRegularFile delegates with resolution`() = runTest {
		val (service, fs) = service(inContainer = false)
		coEvery { fs.isRegularFile(hostPath) } returns false
		
		assertFalse(service.isRegularFile(hostPath))
	}
	
	@Test
	fun `readAllLines delegates and returns content`() = runTest {
		val (service, fs) = service(inContainer = false)
		coEvery { fs.readAllLines(hostPath) } returns Truncated(listOf("a", "b"), false)
		
		assertEquals(listOf("a", "b"), service.readAllLines(hostPath))
	}
	
	@Test
	fun `sha256 delegates with resolution`() = runTest {
		val (service, fs) = service(inContainer = false)
		coEvery { fs.sha256(hostPath) } returns sha
		
		assertEquals(sha, service.sha256(hostPath))
	}
	
	@Test
	fun `write delegates with resolution`() = runTest {
		val (service, fs) = service(inContainer = false)
		coEvery { fs.write(hostPath, sha, listOf("x")) } returns Unit
		
		service.write(hostPath, sha, listOf("x"))
		
		coVerify { fs.write(hostPath, sha, listOf("x")) }
	}
	
	@Test
	fun `glob delegates with resolved cwd`() = runTest {
		val (service, fs) = service(inContainer = true)
		coEvery { fs.glob("**/*.txt", hostPath) } returns listOf(hostPath)
		
		assertEquals(listOf(hostPath), service.glob("**/*.txt", containerPath))
		coVerify { fs.glob("**/*.txt", hostPath) }
	}
}
