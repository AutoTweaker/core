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

package io.github.autotweaker.core.infrastructure.tool

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.FileNotFoundException
import io.github.autotweaker.core.infrastructure.system.RawFileSystemImpl
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class RawFileSystemImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private lateinit var dir: Path
	
	@BeforeTest
	fun setUp() {
		dir = Files.createTempDirectory("rawfs-test")
	}
	
	@AfterTest
	fun tearDown() {
		dir.toFile().deleteRecursively()
	}
	
	private fun file(name: String, content: String = ""): Path =
		dir.resolve(name).also { it.writeText(content) }
	
	private fun shaOf(content: String): Sha256 =
		Sha256(MessageDigest.getInstance("SHA-256").digest(content.toByteArray()))
	
	// region write
	
	@Test
	fun `write with matching expected sha replaces content`() = runTest {
		val path = file("a.txt", "old content")
		val expected = shaOf("old content")
		
		RawFileSystemImpl.write(path, expected, listOf("new line"))
		
		assertEquals("new line", path.readText())
	}
	
	@Test
	fun `write with stale expected fails without modifying file`() = runTest {
		val path = file("a.txt", "original")
		val stale = shaOf("something else")
		
		val ex = assertFailsWith<IllegalStateException> {
			RawFileSystemImpl.write(path, stale, listOf("new"))
		}
		
		assertTrue(ex.message!!.contains("File content changed since read"))
		assertEquals("original", path.readText())
	}
	
	@Test
	fun `write joins lines with unix newline`() = runTest {
		val path = file("a.txt", "x")
		val expected = shaOf("x")
		
		RawFileSystemImpl.write(path, expected, listOf("one", "two", "three"))
		
		assertEquals("one\ntwo\nthree", path.readText())
	}
	
	@Test
	fun `write preserves posix permissions`() = runTest {
		val path = file("a.txt", "content")
		// owner 可读写，group/other 无权限
		path.toFile().setReadable(true, false)
		path.toFile().setWritable(true, false)
		path.toFile().setExecutable(false, false)
		val before = Files.getPosixFilePermissions(path)
		
		RawFileSystemImpl.write(path, shaOf("content"), listOf("updated"))
		
		assertEquals(before, Files.getPosixFilePermissions(path))
	}
	
	@Test
	fun `write on missing file throws FileNotFoundException`() = runTest {
		val path = dir.resolve("missing.txt")
		
		assertFailsWith<FileNotFoundException> {
			RawFileSystemImpl.write(path, shaOf(""), listOf("x"))
		}
	}
	
	// endregion
	
	// region sha256
	
	@Test
	fun `sha256 matches manual digest`() = runTest {
		val path = file("a.bin", "hello world")
		
		assertEquals(shaOf("hello world"), RawFileSystemImpl.sha256(path))
	}
	
	@Test
	fun `sha256 on missing file throws FileNotFoundException`() = runTest {
		assertFailsWith<FileNotFoundException> {
			RawFileSystemImpl.sha256(dir.resolve("missing.txt"))
		}
	}
	
	// endregion
	
	// region read
	
	@Test
	fun `readString returns content without truncation flag`() = runTest {
		val path = file("a.txt", "line one\nline two")
		
		val result = RawFileSystemImpl.readString(path)
		
		assertEquals("line one\nline two", result.content)
		assertFalse(result.truncated)
	}
	
	@Test
	fun `readString on missing file throws FileNotFoundException`() = runTest {
		assertFailsWith<FileNotFoundException> {
			RawFileSystemImpl.readString(dir.resolve("missing.txt"))
		}
	}
	
	@Test
	fun `readAllLines returns lines`() = runTest {
		val path = file("a.txt", "a\nb\nc")
		
		val result = RawFileSystemImpl.readAllLines(path)
		
		assertEquals(listOf("a", "b", "c"), result.content)
		assertFalse(result.truncated)
	}
	
	@Test
	fun `readString on procfs virtual file with zero stat size returns content`() = runTest {
		val path = Path.of("/proc/self/status")
		// procfs 文件 stat 恒为 0 但读取有内容（回归：此前会因 CharArray(0) 返回空字符串）
		val result = RawFileSystemImpl.readString(path)
		
		assertTrue(result.content.isNotBlank())
		assertFalse(result.truncated)
	}
	
	// endregion
	
	// region lineCount
	
	@Test
	fun `lineCount counts newlines`() = runTest {
		assertEquals(3, RawFileSystemImpl.lineCount(file("a.txt", "a\nb\nc\n")))
	}
	
	@Test
	fun `lineCount handles missing trailing newline`() = runTest {
		assertEquals(3, RawFileSystemImpl.lineCount(file("a.txt", "a\nb\nc")))
	}
	
	@Test
	fun `lineCount empty file returns zero`() = runTest {
		assertEquals(0, RawFileSystemImpl.lineCount(file("a.txt", "")))
	}
	
	@Test
	fun `lineCount single line without newline returns one`() = runTest {
		assertEquals(1, RawFileSystemImpl.lineCount(file("a.txt", "solo")))
	}
	
	@Test
	fun `lineCount on missing file throws FileNotFoundException`() = runTest {
		assertFailsWith<FileNotFoundException> {
			RawFileSystemImpl.lineCount(dir.resolve("missing.txt"))
		}
	}
	
	// endregion
	
	// region metadata
	
	@Test
	fun `metadata returns size and type flags`() = runTest {
		val path = file("a.txt", "12345")
		
		val metadata = RawFileSystemImpl.metadata(path)
		
		assertEquals(5, metadata.size)
		assertTrue(metadata.isRegularFile)
		assertFalse(metadata.isDirectory)
		assertNotNull(metadata.owner)
	}
	
	@Test
	fun `metadata on directory marks it as directory`() = runTest {
		val metadata = RawFileSystemImpl.metadata(dir)
		
		assertTrue(metadata.isDirectory)
		assertFalse(metadata.isRegularFile)
	}
	
	@Test
	fun `metadata on missing file throws FileNotFoundException`() = runTest {
		assertFailsWith<FileNotFoundException> {
			RawFileSystemImpl.metadata(dir.resolve("missing.txt"))
		}
	}
	
	// endregion
	
	// region 其他
	
	@Test
	fun `exists and isRegularFile`() = runTest {
		val path = file("a.txt")
		assertTrue(RawFileSystemImpl.exists(path))
		assertTrue(RawFileSystemImpl.isRegularFile(path))
		assertFalse(RawFileSystemImpl.exists(dir.resolve("nope.txt")))
		assertFalse(RawFileSystemImpl.isRegularFile(dir))
	}
	
	@Test
	fun `glob matches files with cross-directory pattern`() = runTest {
		file("a.txt")
		file("b.txt")
		file("c.md")
		Files.createDirectories(dir.resolve("sub"))
		file("sub/d.txt")
		
		val matches = RawFileSystemImpl.glob("**/*.txt", dir)
		
		assertEquals(
			setOf(dir.resolve("a.txt"), dir.resolve("b.txt"), dir.resolve("sub/d.txt")),
			matches.toSet()
		)
	}
	
	// endregion
}
