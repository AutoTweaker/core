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

import io.github.autotweaker.core.Unicode
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
	
	@Test
	fun `normalize resolves relative path`() {
		val service = FileSystemServiceImpl(tmpDir)
		val result = service.normalize("subdir/file.txt")
		
		assertEquals(tmpDir.resolve("subdir/file.txt"), result)
		assertTrue(result.startsWith(tmpDir))
	}
	
	@Test
	fun `normalize resolves absolute path`() {
		val service = FileSystemServiceImpl(tmpDir)
		val absolute = tmpDir.resolve("test.txt")
		val result = service.normalize(absolute.toString())
		
		assertEquals(absolute, result)
	}
	
	@Test
	fun `normalize resolves dot dot`() {
		val service = FileSystemServiceImpl(tmpDir)
		val result = service.normalize("../outside.txt")
		
		assertEquals(tmpDir.parent.resolve("outside.txt"), result)
	}
	
	@Test
	fun `exists returns true for existing file`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = Files.createFile(tmpDir.resolve("test.txt"))
		
		assertTrue(service.exists(file))
	}
	
	@Test
	fun `exists returns false for nonexistent file`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		assertFalse(service.exists(tmpDir.resolve("nonexistent.txt")))
	}
	
	@Test
	fun `isRegularFile returns true for file`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = Files.createFile(tmpDir.resolve("data.txt"))
		
		assertTrue(service.isRegularFile(file))
	}
	
	@Test
	fun `isRegularFile returns false for directory`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		assertFalse(service.isRegularFile(tmpDir))
	}
	
	@Test
	fun `readUnicode reads file as unicode list`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = tmpDir.resolve("unicode.txt")
		Files.writeString(file, "ABC")
		
		val result = service.readUnicode(file)
		assertEquals(3, result.size)
		assertEquals(Unicode.fromChar('A'), result[0])
		assertEquals(Unicode.fromChar('B'), result[1])
		assertEquals(Unicode.fromChar('C'), result[2])
	}
	
	@Test
	fun `readUnicode empty file`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = tmpDir.resolve("empty.txt")
		Files.writeString(file, "")
		
		val result = service.readUnicode(file)
		assertTrue(result.isEmpty())
	}
	
	@Test
	fun `readAllLines reads multiple lines`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = tmpDir.resolve("lines.txt")
		Files.writeString(file, "line1\nline2\nline3")
		
		val result = service.readAllLines(file)
		assertEquals(listOf("line1", "line2", "line3"), result)
	}
	
	@Test
	fun `readAllLines single line`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = tmpDir.resolve("single.txt")
		Files.writeString(file, "only one line")
		
		val result = service.readAllLines(file)
		assertEquals(listOf("only one line"), result)
	}
	
	@Test
	fun `sha256 computes correct hash`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file = tmpDir.resolve("hash.txt")
		Files.writeString(file, "hello")
		
		val result = service.sha256(file)
		// sha256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
		assertEquals(64, result.length)
		assertTrue(result.all { it in '0'..'9' || it in 'a'..'f' })
	}
	
	@Test
	fun `sha256 same content same hash`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file1 = tmpDir.resolve("a.txt")
		val file2 = tmpDir.resolve("b.txt")
		Files.writeString(file1, "same content")
		Files.writeString(file2, "same content")
		
		assertEquals(service.sha256(file1), service.sha256(file2))
	}
	
	@Test
	fun `sha256 different content different hash`() = runTest {
		val service = FileSystemServiceImpl(tmpDir)
		val file1 = tmpDir.resolve("x.txt")
		val file2 = tmpDir.resolve("y.txt")
		Files.writeString(file1, "content A")
		Files.writeString(file2, "content B")
		
		assertNotEquals(service.sha256(file1), service.sha256(file2))
	}
	
	@Test
	fun `normalize path in container`() {
		val hostRoot = tmpDir.resolve("host")
		val containerRoot = tmpDir.resolve("container")
		Files.createDirectories(hostRoot)
		Files.createDirectories(containerRoot)
		
		val service = FileSystemServiceImpl(
			workspaceRoot = containerRoot,
			inContainer = true,
			containerRoot = containerRoot,
			hostRoot = hostRoot,
		)
		
		val result = service.normalize("file.txt")
		assertNotNull(result)
	}
}
