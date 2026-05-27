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

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path
import kotlin.test.*

class ReadTest {
	
	private val defaultSettings: SettingService = mockk<SettingService>().also { svc ->
		every { svc.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	
	private fun readMeta(settings: SettingService = defaultSettings): Read {
		val r = Read()
		r.init(settings)
		return r
	}
	
	private fun container(
		fs: FileSystemService? = null,
		history: ToolCallHistory = mockk(relaxed = true),
		summarize: SummarizeService = mockk(relaxed = true),
	): SimpleContainer {
		val c = SimpleContainer()
		if (fs != null) c.register(FileSystemService::class, fs)
		c.register(ToolCallHistory::class, history)
		c.register(SummarizeService::class, summarize)
		return c
	}
	
	private fun ToolInput(
		functionName: String,
		arguments: JsonObject,
		settings: SettingService = defaultSettings,
	): Tool.ToolInput = Tool.ToolInput(
		functionName = functionName,
		arguments = arguments,
		outputChannel = null,
	)
	
	private fun args(
		filePath: String,
		startLine: Int = 1,
		endLine: Int = 10,
		vararg extras: Pair<String, JsonElement>,
	): JsonObject = buildJsonObject {
		put("file_path", JsonPrimitive(filePath))
		put("start_line", JsonPrimitive(startLine))
		put("end_line", JsonPrimitive(endLine))
		for ((k, v) in extras) put(k, v)
	}
	
	// region meta
	
	@Test
	fun `meta returns correct name`() {
		assertEquals("read", readMeta().meta.name)
	}
	
	@Test
	fun `meta returns three functions`() {
		val meta = readMeta().meta
		assertEquals(3, meta.functions.size)
		assertEquals(setOf("file", "summarize", "unicode"), meta.functions.map { it.name }.toSet())
	}
	
	@Test
	fun `meta file function has line_number optional boolean parameter`() {
		val meta = readMeta().meta
		val fileFunc = meta.functions.find { it.name == "file" }!!
		val lineNumber = fileFunc.parameters["line_number"]!!
		assertFalse(lineNumber.required)
		assertTrue(lineNumber.valueType is Tool.Function.Property.ValueType.BooleanValue)
	}
	
	@Test
	fun `meta summarize function has prompt optional string parameter`() {
		val meta = readMeta().meta
		val summarizeFunc = meta.functions.find { it.name == "summarize" }!!
		val prompt = summarizeFunc.parameters["prompt"]!!
		assertFalse(prompt.required)
		assertTrue(prompt.valueType is Tool.Function.Property.ValueType.StringValue)
	}
	
	@Test
	fun `meta unicode function has max_chars required integer parameter`() {
		val meta = readMeta().meta
		val unicodeFunc = meta.functions.find { it.name == "unicode" }!!
		val maxChars = unicodeFunc.parameters["max_chars"]!!
		assertTrue(maxChars.required)
		assertTrue(maxChars.valueType is Tool.Function.Property.ValueType.IntegerValue)
	}
	
	@Test
	fun `meta common properties are required`() {
		val meta = readMeta().meta
		for (func in meta.functions) {
			if (func.name == "unicode") continue
			assertTrue(func.parameters["file_path"]!!.required)
			assertTrue(func.parameters["start_line"]!!.required)
			assertTrue(func.parameters["end_line"]!!.required)
		}
	}
	
	// endregion
	
	// region path validation
	
	@Test
	fun `normalize throws returns path error`() = runTest {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } throws RuntimeException("bad path")
		
		val read = readMeta()
		val input = ToolInput("file", args("/bad"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("提供的路径不合法，请检查提供的路径参数", result.result)
	}
	
	@Test
	fun `file not found returns not found error`() = runTest {
		val path = Path.of("/tmp/nonexistent")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns false
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/nonexistent"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件不存在或访问被拒绝", result.result)
	}
	
	@Test
	fun `not a regular file returns can not read error`() = runTest {
		val path = Path.of("/tmp/dir")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns false
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/dir"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", result.result)
	}
	
	// endregion
	
	// region file - line validation
	
	@Test
	fun `start line less than 1 returns error`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt", startLine = 0, endLine = 5))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("start_line必须大于或等于1", result.result)
	}
	
	// endregion
	
	// region file - successful reads
	
	@Test
	fun `successful file read with line numbers`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..20).map { "line $it" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns "a".repeat(64)
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns emptyList()
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt", startLine = 1, endLine = 5))
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		assertTrue(result.result.startsWith("a".repeat(64) + "\n"))
		val content = result.result.substringAfter('\n')
		assertTrue(content.contains("1\tline 1"))
		assertTrue(content.contains("5\tline 5"))
		assertFalse(content.contains("6\tline 6"))
	}
	
	@Test
	fun `successful file read without line numbers`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..20).map { "line $it" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns "b".repeat(64)
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns emptyList()
		
		val read = readMeta()
		val a = args("/tmp/f.txt", startLine = 1, endLine = 3, "line_number" to JsonPrimitive(false))
		val input = ToolInput("file", a)
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		val content = result.result.substringAfter('\n')
		assertTrue(content.contains("line 1"))
		assertFalse(content.contains("\tline 1"))
	}
	
	@Test
	fun `end line beyond file length is clamped`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..5).map { "line $it" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns "c".repeat(64)
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns emptyList()
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt", startLine = 1, endLine = 10))
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		val content = result.result.substringAfter('\n')
		assertTrue(content.contains("line 5"), "Should include last line when clamped")
		assertFalse(content.contains("line 6"), "Should not include line beyond file")
	}
	
	// endregion
	
	// region file - errors
	
	@Test
	fun `readAllLines throws returns can not read`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } throws RuntimeException("io error")
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", result.result)
	}
	
	@Test
	fun `sha256 throws returns can not read`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = listOf("hello")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } throws RuntimeException("hash error")
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", result.result)
	}
	
	// endregion
	
	// region file - duplicate detection
	
	@Test
	fun `not duplicate when sha256 differs`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..20).map { "line $it" }
		val sha256 = "e".repeat(64)
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns sha256
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns listOf(
			ToolCallHistory.Entry(
				name = "read_file",
				arguments = """{"file_path":"/tmp/f.txt","start_line":1,"end_line":10}""",
				resultContent = "f".repeat(64) + "\nsome old content",
			)
		)
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt", startLine = 1, endLine = 5))
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		assertFalse(result.result.contains("Duplicate"))
	}
	
	@Test
	fun `not duplicate when new range extends beyond previous`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..20).map { "line $it" }
		val sha256 = "g".repeat(64)
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns sha256
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns listOf(
			ToolCallHistory.Entry(
				name = "read_file",
				arguments = """{"file_path":"/tmp/f.txt","start_line":1,"end_line":5}""",
				resultContent = "$sha256\nsome old content",
			)
		)
		
		val read = readMeta()
		val input = ToolInput("file", args("/tmp/f.txt", startLine = 1, endLine = 10))
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		assertFalse(result.result.contains("Duplicate"))
	}
	
	@Test
	fun `not duplicate when lineNumber differs from history`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..20).map { "line $it" }
		val sha256 = "h".repeat(64)
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		coEvery { fs.sha256(path) } returns sha256
		
		val history = mockk<ToolCallHistory>()
		every { history.getAll() } returns listOf(
			ToolCallHistory.Entry(
				name = "read_file",
				arguments = """{"file_path":"/tmp/f.txt","start_line":1,"end_line":10,"line_number":false}""",
				resultContent = "$sha256\nsome old content",
			)
		)
		
		val read = readMeta()
		val a = args("/tmp/f.txt", startLine = 3, endLine = 5, "line_number" to JsonPrimitive(true))
		val input = ToolInput("file", a)
		val result = read.coreExec(container(fs = fs, history = history), input)
		
		assertTrue(result.success)
		assertFalse(result.result.contains("Duplicate"))
	}
	
	// endregion
	
	// region summarize
	
	@Test
	fun `successful summarize within output limit`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..30).map { "this is line number $it with some content to make it long enough" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		
		val summarizeService = mockk<SummarizeService>()
		coEvery { summarizeService.summarize(any(), any()) } returns "summary"
		
		val read = readMeta()
		val input = ToolInput("summarize", args("/tmp/f.txt", startLine = 1, endLine = 30))
		val result = read.coreExec(container(fs = fs, summarize = summarizeService), input)
		
		assertTrue(result.success)
		assertEquals("summary", result.result)
	}
	
	@Test
	fun `summarize with custom prompt`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..30).map { "this is line number $it with some content to make it long enough" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		
		val summarizeService = mockk<SummarizeService>()
		coEvery { summarizeService.summarize(any(), any()) } returns "custom summary"
		
		val read = readMeta()
		val a = args("/tmp/f.txt", startLine = 1, endLine = 30, "prompt" to JsonPrimitive("extra instruction"))
		val input = ToolInput("summarize", a)
		val result = read.coreExec(container(fs = fs, summarize = summarizeService), input)
		
		assertTrue(result.success)
		assertEquals("custom summary", result.result)
		
		coVerify {
			summarizeService.summarize(any(), withArg {
				assertTrue(it.contains("extra instruction"))
			})
		}
	}
	
	@Test
	fun `summarize service throws returns error`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val lines = (1..30).map { "this is line number $it with some content to make it long enough" }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } returns lines
		
		val summarizeService = mockk<SummarizeService>()
		coEvery { summarizeService.summarize(any(), any()) } throws RuntimeException("api error")
		
		val read = readMeta()
		val input = ToolInput("summarize", args("/tmp/f.txt", startLine = 1, endLine = 30))
		val result = read.coreExec(container(fs = fs, summarize = summarizeService), input)
		
		assertFalse(result.success)
		assertTrue(result.result.contains("api error"))
	}
	
	@Test
	fun `summarize readAllLines throws returns can not read`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readAllLines(path) } throws RuntimeException("io error")
		
		val read = readMeta()
		val input = ToolInput("summarize", args("/tmp/f.txt"))
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", result.result)
	}
	
	// endregion
	
	// region unicode
	
	@Test
	fun `unicode max chars exceeded returns error`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		
		val read = readMeta()
		val a = buildJsonObject {
			put("file_path", JsonPrimitive("/tmp/f.txt"))
			put("max_chars", JsonPrimitive(501))
		}
		val input = ToolInput("unicode", a)
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertTrue(result.result.contains("500"))
	}
	
	@Test
	fun `successful unicode read`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readUnicode(path) } returns listOf(
			Unicode.fromChar('H'),
			Unicode.fromChar('e'),
			Unicode.fromChar('l'),
			Unicode.fromChar('l'),
			Unicode.fromChar('o'),
		)
		
		val read = readMeta()
		val a = buildJsonObject {
			put("file_path", JsonPrimitive("/tmp/f.txt"))
			put("max_chars", JsonPrimitive(10))
		}
		val input = ToolInput("unicode", a)
		val result = read.coreExec(container(fs = fs), input)
		
		assertTrue(result.success)
		assertTrue(result.result.contains("\\u0048"))
		assertTrue(result.result.contains("\\u0065"))
	}
	
	@Test
	fun `unicode read takes only up to max chars`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val chars = ('A'..'Z').map { Unicode.fromChar(it) }
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readUnicode(path) } returns chars
		
		val read = readMeta()
		val a = buildJsonObject {
			put("file_path", JsonPrimitive("/tmp/f.txt"))
			put("max_chars", JsonPrimitive(5))
		}
		val input = ToolInput("unicode", a)
		val result = read.coreExec(container(fs = fs), input)
		
		assertTrue(result.success)
		assertEquals(5 * 6, result.result.length)
	}
	
	@Test
	fun `unicode read fails returns can not read`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readUnicode(path) } throws RuntimeException("read error")
		
		val read = readMeta()
		val a = buildJsonObject {
			put("file_path", JsonPrimitive("/tmp/f.txt"))
			put("max_chars", JsonPrimitive(10))
		}
		val input = ToolInput("unicode", a)
		val result = read.coreExec(container(fs = fs), input)
		
		assertFalse(result.success)
		assertEquals("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", result.result)
	}
	
	@Test
	fun `unicode read with max chars equal to limit is allowed`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		coEvery { fs.readUnicode(path) } returns listOf(Unicode.fromChar('A'))
		
		val read = readMeta()
		val a = buildJsonObject {
			put("file_path", JsonPrimitive("/tmp/f.txt"))
			put("max_chars", JsonPrimitive(500))
		}
		val input = ToolInput("unicode", a)
		val result = read.coreExec(container(fs = fs), input)
		
		assertTrue(result.success)
	}
	
	// endregion
	
	// region unknown function
	
	@Test
	fun `unknown function throws IllegalArgumentException`() = runTest {
		val path = Path.of("/tmp/f.txt")
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		coEvery { fs.exists(path) } returns true
		coEvery { fs.isRegularFile(path) } returns true
		
		val read = readMeta()
		val input = ToolInput("nonexistent", args("/tmp/f.txt"))
		val ex = assertFailsWith<IllegalArgumentException> { read.coreExec(container(fs = fs), input) }
		assertEquals("Unknown function: nonexistent", ex.message)
	}
	
	// endregion
}
