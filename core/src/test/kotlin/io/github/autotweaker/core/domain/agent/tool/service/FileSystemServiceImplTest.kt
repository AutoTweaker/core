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
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.tool.RawFileSystemImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FileSystemServiceImplTest {
	
	private lateinit var tmpDir: Path
	
	@BeforeTest
	fun setUp() {
		tmpDir = Files.createTempDirectory("fs-test")
	}
	
	@AfterTest
	fun tearDown() {
		tmpDir.toFile().deleteRecursively()
	}
	
	private fun service(
		containerRoot: Path = tmpDir,
		hostRoot: Path = tmpDir
	): FileSystemServiceImpl {
		val env = mockk<AgentEnvironment>()
		every { env.workspace } returns WorkspaceMeta("test", path = containerRoot)
		every { env.containerConfig } returns ContainerConfig(workDir = containerRoot, workspaceHostPath = hostRoot)
		return FileSystemServiceImpl(RawFileSystemImpl, env)
	}
	
	@Test
	fun `normalize resolves relative path`() {
		val result = service().normalize("subdir/file.txt")
		assertEquals(tmpDir.resolve("subdir/file.txt"), result)
		assertTrue(result.startsWith(tmpDir))
	}
	
	@Test
	fun `normalize resolves absolute path`() {
		val absolute = tmpDir.resolve("test.txt")
		val result = service().normalize(absolute.toString())
		assertEquals(absolute, result)
	}
	
	@Test
	fun `normalize resolves dot dot`() {
		val result = service().normalize("../outside.txt")
		assertEquals(tmpDir.parent.resolve("outside.txt"), result)
	}
	
	@Test
	fun `exists returns true for existing file`() = runTest {
		val file = tmpDir.resolve("test.txt")
		file.toFile().writeText("hello")
		assertTrue(service().exists(file))
	}
	
	@Test
	fun `exists returns false for missing file`() = runTest {
		val missing = tmpDir.resolve("missing.txt")
		assertFalse(service().exists(missing))
	}
	
	@Test
	fun `isRegularFile returns true for file`() = runTest {
		val file = tmpDir.resolve("test.txt")
		file.toFile().writeText("hello")
		assertTrue(service().isRegularFile(file))
	}
	
	@Test
	fun `isRegularFile returns false for directory`() = runTest {
		assertFalse(service().isRegularFile(tmpDir))
	}
	
	@Test
	fun `readAllLines reads file content`() = runTest {
		val file = tmpDir.resolve("test.txt")
		file.toFile().writeText("line1\nline2\nline3")
		val lines = service().readAllLines(file)
		assertEquals(listOf("line1", "line2", "line3"), lines)
	}
	
	@Test
	fun `sha256 computes hash`() = runTest {
		val file = tmpDir.resolve("test.txt")
		file.toFile().writeText("hello")
		val hash = service().sha256(file)
		assertEquals(64, hash.length)
	}
	
	@Test
	fun `readUnicode returns unicode values`() = runTest {
		val file = tmpDir.resolve("test.txt")
		file.toFile().writeText("Hello")
		val result = service().readUnicode(file)
		assertTrue(result.isNotEmpty())
	}
	
	@Test
	fun `normalize with container mapping`() {
		val hostRoot = tmpDir.resolve("host")
		val containerRoot = hostRoot.resolve("workspace")
		Files.createDirectories(hostRoot)
		Files.createDirectories(containerRoot)

		val svc = service(containerRoot = containerRoot, hostRoot = hostRoot)
		val result = svc.normalize("file.txt")
		assertNotNull(result)
	}
}
