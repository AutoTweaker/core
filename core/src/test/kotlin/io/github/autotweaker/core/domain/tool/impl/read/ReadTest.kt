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

package io.github.autotweaker.core.domain.tool.impl.read

import io.github.autotweaker.api.generated.tool.args.ReadArgs
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.api.types.tool.read.ReadRequest
import io.github.autotweaker.api.types.tool.read.ReadResult
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.FileAccessDeniedException
import io.github.autotweaker.core.domain.port.FileContent
import io.github.autotweaker.core.domain.port.FileNotFoundException
import io.github.autotweaker.core.domain.tool.ServiceContainer
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import kotlin.test.*

class ReadTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val read = Read()
	private val path = Path.of("test.txt")
	private val sha = Sha256(ByteArray(32) { it.toByte() })
	
	private fun container(fs: FileSystemService): ServiceContainer {
		val c = ServiceContainer()
		c.register(fs)
		return c
	}
	
	private fun fileArgs(
		startLine: Int = 1,
		endLine: Int = 2,
		lineNumber: Boolean? = null,
		unicodeEscape: Boolean? = null,
	) = ReadArgs.File("test.txt", startLine, endLine, lineNumber, unicodeEscape)
	
	private fun summarizeArgs(
		startLine: Int = 1,
		endLine: Int = 1,
		prompt: String? = null,
	) = ReadArgs.Summarize("test.txt", startLine, endLine, prompt)
	
	private fun mockFs(
		exists: Boolean = true,
		isRegularFile: Boolean = true,
		content: String = "",
	): FileSystemService {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		every { fs.relativize(any()) } returns path
		coEvery { fs.exists(path) } returns exists
		coEvery { fs.isRegularFile(path) } returns isRegularFile
		coEvery { fs.sha256(path) } returns sha
		coEvery { fs.read(path) } returns FileContent(content, false, sha)
		return fs
	}
	
	private fun history(vararg entries: Pair<ReadRequest, ReadResult>): ToolCallHistory {
		val h = mockk<ToolCallHistory>()
		every { h.getAll(any<KSerializer<ReadRequest>>(), any<KSerializer<ReadResult>>()) } returns entries.toList()
		return h
	}
	
	private fun summarizeService(output: String): SummarizeService {
		val s = mockk<SummarizeService>()
		coEvery { s.invoke(any()) } returns output
		return s
	}
	
	private fun request(request: ReadRequest): JsonElement =
		Json.encodeToJsonElement(ReadRequest.serializer(), request)
	
	// region resolve
	
	@Test
	fun `resolve valid file request returns Ready`() = runTest {
		val result = read.resolve(container(mockFs()), fileArgs())
		
		assertIs<Tool.ResolveResult.Ready>(result)
		val resolved = Json.decodeFromJsonElement(ReadRequest.serializer(), result.result)
		assertIs<ReadRequest.File>(resolved)
		assertEquals(path, resolved.path)
		assertEquals(1, resolved.startLine)
		assertEquals(2, resolved.endLine)
		assertTrue(resolved.lineNumber)
		assertFalse(resolved.unicodeEscape)
	}
	
	@Test
	fun `resolve valid summarize request returns Ready`() = runTest {
		val result = read.resolve(container(mockFs()), summarizeArgs(prompt = "focus on errors"))
		
		assertIs<Tool.ResolveResult.Ready>(result)
		val resolved = Json.decodeFromJsonElement(ReadRequest.serializer(), result.result)
		assertIs<ReadRequest.Summarize>(resolved)
		assertEquals(path, resolved.path)
		assertEquals("focus on errors", resolved.prompt)
		assertTrue(resolved.lineNumber)
	}
	
	@Test
	fun `resolve start line below 1 rejected`() = runTest {
		val result = read.resolve(container(mockFs()), fileArgs(startLine = 0))
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("start_line必须大于或等于1", result.reason)
	}
	
	@Test
	fun `resolve end line smaller than start line rejected`() = runTest {
		val result = read.resolve(container(mockFs()), fileArgs(startLine = 3, endLine = 2))
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("start_line不能大于end_line", result.reason)
	}
	
	@Test
	fun `resolve too many lines rejected for file`() = runTest {
		val result = read.resolve(container(mockFs()), fileArgs(startLine = 1, endLine = 5001))

		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("读取的行数过多（5001），上限为5000", result.reason)
	}

	@Test
	fun `resolve too many lines rejected for summarize`() = runTest {
		val result = read.resolve(container(mockFs()), summarizeArgs(startLine = 1, endLine = 10001))

		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("读取的行数过多（10001），上限为10000", result.reason)
	}
	
	@Test
	fun `resolve non-existent file rejected`() = runTest {
		val result = read.resolve(container(mockFs(exists = false)), fileArgs())
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("文件test.txt不存在或访问被拒绝", result.reason)
	}
	
	@Test
	fun `resolve non-regular file rejected`() = runTest {
		val result = read.resolve(container(mockFs(isRegularFile = false)), fileArgs())
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("文件test.txt不是一个可读取的普通文件", result.reason)
	}
	
	@Test
	fun `resolve path outside workspace rejected`() = runTest {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		every { fs.relativize(any()) } returns path
		coEvery { fs.exists(path) } throws PathOutsideWorkspaceException(path)
		val result = read.resolve(container(fs), fileArgs())
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("错误：请求的文件路径在工作目录外部", result.reason)
	}
	
	@Test
	fun `resolve normalize failure rejected`() = runTest {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } throws IllegalArgumentException("bad path")
		val result = read.resolve(container(fs), fileArgs())
		
		assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals("提供的路径不合法，请检查提供的路径参数", result.reason)
	}
	
	// endregion
	
	// region exec - file
	
	@Test
	fun `exec file returns content with sha256 prefix and line numbers`() = runTest {
		val c = container(mockFs(content = "line1\nline2"))
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 2, true, false)), Channel(Channel.UNLIMITED))
		
		assertTrue(result.success)
		assertEquals("$sha\n1\tline1\n2\tline2\n", result.result)
	}
	
	@Test
	fun `exec file without line numbers`() = runTest {
		val c = container(mockFs(content = "line1\nline2"))
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 2, false, false)), Channel(Channel.UNLIMITED))
		
		assertTrue(result.success)
		assertEquals("$sha\nline1\nline2\n", result.result)
	}
	
	@Test
	fun `exec file with unicode escape`() = runTest {
		val c = container(mockFs(content = "中"))
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 1, false, true)), Channel(Channel.UNLIMITED))
		
		assertTrue(result.success)
		assertEquals("$sha\n\\u4E2D\n", result.result)
	}
	
	@Test
	fun `exec file duplicate returns duplicate message`() = runTest {
		val c = container(mockFs(content = "line1\nline2"))
		c.register(
			history(ReadRequest.File(path, path, 1, 2, true, false) to ReadResult(sha, "$sha\nold", false))
		)
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 2, true, false)), Channel(Channel.UNLIMITED))
		
		assertTrue(result.success)
		assertEquals("读取的文件内容与文件哈希${sha}时的读取相同", result.result)
	}
	
	@Test
	fun `exec file start line beyond file size returns error`() = runTest {
		val c = container(mockFs(content = "a\nb\nc"))
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 5, 5, true, false)), Channel(Channel.UNLIMITED))
		
		assertFalse(result.success)
		assertEquals("start_line超出了文件可读行数（3）", result.result)
	}
	
	@Test
	fun `exec file not found returns error`() = runTest {
		val fs = mockFs()
		coEvery { fs.read(path) } throws FileNotFoundException(NoSuchFileException(path.toFile()))
		val c = container(fs)
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 1, true, false)), Channel(Channel.UNLIMITED))

		assertFalse(result.success)
		assertEquals("文件test.txt不存在或访问被拒绝", result.result)
	}
	
	@Test
	fun `exec file access denied returns error`() = runTest {
		val fs = mockFs(content = "a")
		coEvery { fs.read(path) } throws FileAccessDeniedException(IllegalStateException())
		val c = container(fs)
		c.register(history())
		val result =
			read.execute(c, request(ReadRequest.File(path, path, 1, 1, true, false)), Channel(Channel.UNLIMITED))
		
		assertFalse(result.success)
		assertEquals("当前用户没有权限读取这个文件", result.result)
	}
	
	// endregion
	
	// region exec - summarize
	
	@Test
	fun `exec summarize returns summary`() = runTest {
		val c = container(mockFs(content = "x".repeat(600)))
		c.register(summarizeService("summary result"))
		val result = read.execute(c, request(ReadRequest.Summarize(path, path, 1, 1, null)), Channel(Channel.UNLIMITED))
		
		assertTrue(result.success)
		assertEquals("summary result", result.result)
	}
	
	@Test
	fun `exec summarize too few chars returns error`() = runTest {
		val c = container(mockFs(content = "short"))
		c.register(summarizeService("unused"))
		val result = read.execute(c, request(ReadRequest.Summarize(path, path, 1, 1, null)), Channel(Channel.UNLIMITED))
		
		assertFalse(result.success)
		assertTrue(result.result.contains("必须大于500"))
	}
	
	@Test
	fun `exec summarize output truncated`() = runTest {
		val c = container(mockFs(content = "x".repeat(600)))
		c.register(summarizeService("y".repeat(60000)))
		val result = read.execute(c, request(ReadRequest.Summarize(path, path, 1, 1, null)), Channel(Channel.UNLIMITED))

		assertTrue(result.success)
		assertTrue(result.result.startsWith("y".repeat(50000)))
		assertTrue(
			result.result.endsWith("[总结器输出内容过多（共60000字符），后续内容已被截断，请尝试修改总结器提示词]")
		)
	}
	
	@Test
	fun `exec summarize failure returns error`() = runTest {
		val s = mockk<SummarizeService>()
		coEvery { s.invoke(any()) } throws RuntimeException("boom")
		val c = container(mockFs(content = "x".repeat(600)))
		c.register(s)
		val result = read.execute(c, request(ReadRequest.Summarize(path, path, 1, 1, null)), Channel(Channel.UNLIMITED))
		
		assertFalse(result.success)
		assertEquals("总结器出错，请及时告知用户：RuntimeException: boom", result.result)
	}
	
	// endregion
}
