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

package io.github.autotweaker.core.domain.tool.impl.edit

import io.github.autotweaker.api.base.unifiedDiff
import io.github.autotweaker.api.generated.tool.args.EditArgs
import io.github.autotweaker.api.generated.tool.args.Replacement
import io.github.autotweaker.api.generated.tool.args.UnescapeConfig
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.tool.edit.EditRequest
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.FileContent
import io.github.autotweaker.core.domain.port.exception.FileNotFoundException
import io.github.autotweaker.core.domain.tool.ServiceContainer
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val edit = Edit()
	private val path = Path.of("test.txt")
	
	private fun container(fs: FileSystemService): ServiceContainer {
		val c = ServiceContainer()
		c.register(fs)
		return c
	}
	
	private fun sha(content: String) = Sha256.hash(content)
	
	private fun replacement(
		lineFrom: Int? = null,
		lineTo: Int? = null,
		oldString: String,
		newString: String,
		unescapeOld: UnescapeConfig? = null,
		unescapeNew: UnescapeConfig? = null,
	) = Replacement(lineFrom, lineTo, oldString, unescapeOld, newString, unescapeNew)
	
	private fun editArgs(content: String, edits: List<Replacement>, sha256: String = sha(content).toString()) =
		EditArgs.File("test.txt", sha256, edits)
	
	private fun mockFs(
		content: String,
		truncated: Boolean = false,
		fileSha: Sha256 = sha(content),
	): FileSystemService {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } returns path
		every { fs.displayPath(any()) } returns path
		coEvery { fs.read(path) } returns FileContent(content, truncated, fileSha)
		return fs
	}
	
	
	private fun decodeRequest(result: Tool.ResolveResult): EditRequest {
		val ready = assertIs<Tool.ResolveResult.Ready>(result)
		return Json.decodeFromJsonElement(EditRequest.serializer(), ready.result)
	}
	
	private fun assertRejected(result: Tool.ResolveResult, reason: String) {
		val rejected = assertIs<Tool.ResolveResult.Rejected>(result)
		assertEquals(reason, rejected.reason)
	}
	
	// region resolve - 成功路径
	
	@Test
	fun `resolve single replacement returns Ready with new content`() = runTest {
		val content = "line1\nline2\nline3"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(content, listOf(replacement(oldString = "line2", newString = "line2x")))
		)
		
		val request = decodeRequest(result)
		assertEquals(path, request.path)
		assertEquals(path, request.displayPath)
		assertEquals(content, request.expected.first)
		assertEquals(sha(content), request.expected.second)
		assertEquals("line1\nline2x\nline3", request.newContent)
		assertTrue(request.skippedNoMatch.isEmpty())
		assertTrue(request.skippedNotUnique.isEmpty())
	}
	
	@Test
	fun `resolve multiple replacements without overlap applies all`() = runTest {
		val content = "line1\nline2\nline3\nline4"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 1, lineTo = 2, oldString = "line1", newString = "L1"),
					replacement(lineFrom = 3, lineTo = 4, oldString = "line3", newString = "L3"),
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("L1\nline2\nL3\nline4", request.newContent)
		assertTrue(request.skippedNoMatch.isEmpty())
	}
	
	@Test
	fun `resolve default line range covers whole file`() = runTest {
		val content = "a\nb"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(content, listOf(replacement(oldString = "b", newString = "B")))
		)
		
		val request = decodeRequest(result)
		assertEquals("a\nB", request.newContent)
	}
	
	@Test
	fun `resolve partial match skipped records no match`() = runTest {
		val content = "line1\nline2\nline3"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 1, lineTo = 1, oldString = "line1", newString = "L1"),
					replacement(lineFrom = 3, lineTo = 3, oldString = "zzz", newString = "z"),
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("L1\nline2\nline3", request.newContent)
		assertEquals(listOf(3 to 3), request.skippedNoMatch)
		assertTrue(request.skippedNotUnique.isEmpty())
	}
	
	@Test
	fun `resolve limited line range only matches within the range`() = runTest {
		val content = "same\nother\nsame"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 3, lineTo = 3, oldString = "same", newString = "changed")
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("same\nother\nchanged", request.newContent)
	}
	
	// endregion
	
	// region resolve - 参数与读取失败
	
	@Test
	fun `resolve normalize failure rejected`() = runTest {
		val fs = mockk<FileSystemService>()
		every { fs.normalize(any()) } throws IllegalArgumentException("bad path")
		val result = edit.resolve(
			container(fs),
			editArgs("x", listOf(replacement(oldString = "x", newString = "y")))
		)
		
		assertRejected(result, "提供的路径不合法，请检查提供的路径参数")
	}
	
	@Test
	fun `resolve invalid sha256 hex rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("line")),
			editArgs("line", listOf(replacement(oldString = "line", newString = "l")), sha256 = "1234")
		)
		
		val rejected = assertIs<Tool.ResolveResult.Rejected>(result)
		assertTrue(rejected.reason.startsWith("无效的哈希："))
	}
	
	@Test
	fun `resolve sha256 mismatch rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("line")),
			editArgs("line", listOf(replacement(oldString = "line", newString = "l")), sha256 = sha("other").toString())
		)
		
		assertRejected(result, "编辑文件失败，SHA256不匹配，文件已被外部更新，请重新读取文件")
	}
	
	@Test
	fun `resolve read failure rejected`() = runTest {
		val fs = mockFs("line")
		coEvery { fs.read(path) } throws FileNotFoundException(NoSuchFileException(path.toFile()))
		val result = edit.resolve(
			container(fs),
			editArgs("line", listOf(replacement(oldString = "line", newString = "l")))
		)
		
		val rejected = assertIs<Tool.ResolveResult.Rejected>(result)
		assertTrue(rejected.reason.startsWith("读取目标文件时出错："))
	}
	
	@Test
	fun `resolve truncated file rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("line", truncated = true)),
			editArgs("line", listOf(replacement(oldString = "line", newString = "l")))
		)
		
		assertRejected(result, "目标文件可能超出了10MB，程序无法完整读取，也无法计算更新后的完整内容")
	}
	
	@Test
	fun `resolve empty edits rejected`() = runTest {
		val result = edit.resolve(container(mockFs("line")), editArgs("line", emptyList()))
		
		assertRejected(result, "edits不能为空")
	}
	
	// endregion
	
	// region resolve - 编辑段校验
	
	@Test
	fun `resolve line_from below 1 rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs(
				"l1\nl2\nl3",
				listOf(replacement(lineFrom = 0, oldString = "l1", newString = "x"))
			)
		)
		
		assertRejected(
			result,
			"以下1个编辑段存在错误，文件没有被更新：\n编辑段0-3存在错误：line_from必须大于或等于1\n"
		)
	}
	
	@Test
	fun `resolve line_to before line_from rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs(
				"l1\nl2\nl3",
				listOf(replacement(lineFrom = 3, lineTo = 2, oldString = "l3", newString = "x"))
			)
		)
		
		assertRejected(
			result,
			"以下1个编辑段存在错误，文件没有被更新：\n编辑段3-2存在错误：line_to不能小于line_from\n"
		)
	}
	
	@Test
	fun `resolve line_to beyond file rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs(
				"l1\nl2\nl3",
				listOf(replacement(lineFrom = 1, lineTo = 4, oldString = "l1", newString = "x"))
			)
		)
		
		assertRejected(
			result,
			"以下1个编辑段存在错误，文件没有被更新：\n编辑段1-4存在错误：line_to超出了文件总行数（3行）\n"
		)
	}
	
	@Test
	fun `resolve empty old_string rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs("l1\nl2\nl3", listOf(replacement(oldString = "", newString = "x")))
		)
		
		assertRejected(
			result,
			"以下1个编辑段存在错误，文件没有被更新：\n编辑段1-3存在错误：old_string不能为空\n"
		)
	}
	
	@Test
	fun `resolve invalid unicode escape in old_string rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2")),
			editArgs(
				"l1\nl2",
				listOf(
					replacement(
						oldString = "\\uZZZZ",
						newString = "x",
						unescapeOld = UnescapeConfig.DEFAULT,
					)
				)
			)
		)
		
		val rejected = assertIs<Tool.ResolveResult.Rejected>(result)
		assertTrue(
			rejected.reason.startsWith(
				"以下1个编辑段存在错误，文件没有被更新：\n编辑段1-2存在错误：old_string中包含非法或未知的转义序列："
			)
		)
		assertTrue(rejected.reason.contains("not a valid hex number"))
	}
	
	@Test
	fun `resolve multiple invalid replacements reports all`() = runTest {
		val content = "l1\nl2\nl3"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 1, lineTo = 1, oldString = "", newString = "x"),
					replacement(lineFrom = 0, oldString = "l2", newString = "y"),
				)
			)
		)
		
		assertRejected(
			result,
			"以下2个编辑段存在错误，文件没有被更新：\n" +
					"编辑段1-1存在错误：old_string不能为空\n" +
					"编辑段0-3存在错误：line_from必须大于或等于1\n"
		)
	}
	
	@Test
	fun `resolve overlapping replacements rejected`() = runTest {
		val content = "l1\nl2\nl3\nl4"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 1, lineTo = 3, oldString = "x", newString = "a"),
					replacement(lineFrom = 2, lineTo = 4, oldString = "y", newString = "b"),
				)
			)
		)
		
		assertRejected(result, "编辑段1-3与2-4重叠")
	}
	
	@Test
	fun `resolve adjacent replacements are allowed`() = runTest {
		val content = "l1\nl2\nl3\nl4"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(lineFrom = 1, lineTo = 2, oldString = "l1", newString = "A"),
					replacement(lineFrom = 3, lineTo = 4, oldString = "l4", newString = "D"),
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("A\nl2\nl3\nD", request.newContent)
	}
	
	// endregion
	
	// region resolve - 匹配失败
	
	@Test
	fun `resolve no match rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs("l1\nl2\nl3", listOf(replacement(oldString = "zzz", newString = "x")))
		)
		
		assertRejected(
			result,
			"以下指定的范围内没有old_string的匹配项。\n1-3\n" +
					"请重新读取文件确认当前状态符合预期，并确保提供的字符精确。" +
					"你可以使用read工具的Unicode转义模式获取指定片段的精确内容\n"
		)
	}
	
	@Test
	fun `resolve multiple no match ranges reported`() = runTest {
		val result = edit.resolve(
			container(mockFs("l1\nl2\nl3")),
			editArgs(
				"l1\nl2\nl3",
				listOf(
					replacement(lineFrom = 1, lineTo = 1, oldString = "zzz", newString = "a"),
					replacement(lineFrom = 2, lineTo = 2, oldString = "qqq", newString = "b"),
				)
			)
		)
		
		assertRejected(
			result,
			"以下指定的范围内没有old_string的匹配项。\n1-1, 2-2\n" +
					"请重新读取文件确认当前状态符合预期，并确保提供的字符精确。" +
					"你可以使用read工具的Unicode转义模式获取指定片段的精确内容\n"
		)
	}
	
	@Test
	fun `resolve not unique match rejected`() = runTest {
		val result = edit.resolve(
			container(mockFs("x\nx\ny")),
			editArgs("x\nx\ny", listOf(replacement(oldString = "x", newString = "z")))
		)
		
		assertRejected(
			result,
			"以下指定的范围内存在多处old_string的匹配项。\n1-3\n" +
					"请尝试缩小行区间或在old_string中提供更多上下文\n"
		)
	}
	
	@Test
	fun `resolve unique match within limited range succeeds`() = runTest {
		val content = "x\nx\ny"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(replacement(lineFrom = 2, lineTo = 2, oldString = "x", newString = "z"))
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("x\nz\ny", request.newContent)
	}
	
	// endregion
	
	// region resolve - Unicode 转义
	
	@Test
	fun `resolve disabled unescape keeps literal backslash text`() = runTest {
		val content = "value \\u0041"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(replacement(oldString = "\\u0041", newString = "replacement"))
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("value replacement", request.newContent)
	}
	
	@Test
	fun `resolve old_string unescaped matches unicode char`() = runTest {
		val content = "你好\n中\n结束"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(
						oldString = "\\u4E2D",
						newString = "中文",
						unescapeOld = UnescapeConfig.DEFAULT,
					)
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("你好\n中文\n结束", request.newContent)
	}
	
	@Test
	fun `resolve new_string unescaped writes unicode char`() = runTest {
		val content = "a\nb\nc"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(
						oldString = "b",
						newString = "\\u4E2D",
						unescapeNew = UnescapeConfig.DEFAULT,
					)
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("a\n中\nc", request.newContent)
	}
	
	@Test
	fun `resolve lenient unescape keeps invalid escape literal`() = runTest {
		val content = "before\n\\uZZZZ\nafter"
		val result = edit.resolve(
			container(mockFs(content)),
			editArgs(
				content,
				listOf(
					replacement(
						oldString = "\\uZZZZ",
						newString = "ok",
						unescapeOld = UnescapeConfig.LENIENT_MODE,
					)
				)
			)
		)
		
		val request = decodeRequest(result)
		assertEquals("before\nok\nafter", request.newContent)
	}
	
	// endregion
	
	// region execute
	
	@Test
	fun `execute updates file and returns diff with new sha`() = runTest {
		val old = "line1\nline2\nline3"
		val new = "line1\nline2x\nline3"
		val oldSha = sha(old)
		val newSha = sha(new)
		val fs = mockFs(old)
		coEvery { fs.update(path, oldSha, new) } returns newSha
		val c = container(fs)
		
		val result = edit.execute(
			c,
			Json.encodeToJsonElement(
				EditRequest.serializer(),
				EditRequest(path, path, old to oldSha, new, emptyList(), emptyList())
			),
			Channel(Channel.UNLIMITED)
		)
		
		coVerify { fs.update(path, oldSha, new) }
		assertTrue(result.success)
		val diff = unifiedDiff(old, new)
		assertEquals("已更新文件 $path，当前 SHA256：$newSha，文件变更：\n$diff", result.result)
	}
	
	@Test
	fun `execute appends skipped no match message`() = runTest {
		val old = "line1\nline2\nline3"
		val new = "line1\nline2x\nline3"
		val oldSha = sha(old)
		val newSha = sha(new)
		val fs = mockFs(old)
		coEvery { fs.update(path, oldSha, new) } returns newSha
		val c = container(fs)
		
		val result = edit.execute(
			c,
			Json.encodeToJsonElement(
				EditRequest.serializer(),
				EditRequest(path, path, old to oldSha, new, listOf(3 to 3), emptyList())
			),
			Channel(Channel.UNLIMITED)
		)
		
		assertTrue(result.success)
		val diff = unifiedDiff(old, new)
		val noMatch = "以下指定的范围内没有old_string的匹配项。\n3-3\n" +
				"请重新读取文件确认当前状态符合预期，并确保提供的字符精确。" +
				"你可以使用read工具的Unicode转义模式获取指定片段的精确内容"
		assertEquals(
			"已更新文件 $path，当前 SHA256：$newSha，文件变更：\n$diff\n\n$noMatch\n",
			result.result
		)
	}
	
	@Test
	fun `execute with unchanged content reports unchanged`() = runTest {
		val old = "line1\nline2"
		val oldSha = sha(old)
		val fs = mockFs(old)
		coEvery { fs.update(path, oldSha, old) } returns oldSha
		val c = container(fs)
		
		val result = edit.execute(
			c,
			Json.encodeToJsonElement(
				EditRequest.serializer(),
				EditRequest(path, path, old to oldSha, old, emptyList(), emptyList())
			),
			Channel(Channel.UNLIMITED)
		)
		
		assertTrue(result.success)
		assertEquals("已更新文件 $path，当前 SHA256：$oldSha，文件变更：\nUNCHANGED", result.result)
	}
	
	// endregion
}
