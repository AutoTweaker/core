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

package io.github.autotweaker.core.domain.tool

import io.github.autotweaker.api.tool.Tool
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class ToolMetaBuildTest {
	
	// region test data classes
	
	@Serializable
	private data class SimpleArgs(
		val command: String,
		val timeoutSeconds: Int = 60,
		val verbose: Boolean = false,
		val tags: List<String> = emptyList(),
	)
	
	@Serializable
	private sealed class MultiArgs {
		@Serializable
		data class File(
			val filePath: String,
			val startLine: Int,
			val endLine: Int,
			val lineNumber: Boolean = true,
		) : MultiArgs()
		
		@Serializable
		data class Summarize(
			val filePath: String,
			val startLine: Int,
			val endLine: Int,
			val prompt: String? = null,
		) : MultiArgs()
		
		@Serializable
		data class Unicode(
			val filePath: String,
			val maxChars: Int,
		) : MultiArgs()
	}
	
	// endregion
	
	// region helpers
	
	private fun mockSimpleTool(): Tool<SimpleArgs> {
		val tool = mockk<Tool<SimpleArgs>>()
		every { tool.name } returns "simple"
		every { tool.description } returns "A simple tool"
		every { tool.argsSerializer } returns SimpleArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			SimpleArgs::command to "The command to execute",
			SimpleArgs::timeoutSeconds to "Timeout in seconds, default 60",
			SimpleArgs::verbose to "Enable verbose output",
			SimpleArgs::tags to "List of tags",
		)
		coEvery { tool.describeFunctions() } returns emptyMap()
		return tool
	}
	
	private fun mockMultiTool(): Tool<MultiArgs> {
		val tool = mockk<Tool<MultiArgs>>()
		every { tool.name } returns "read"
		every { tool.description } returns "Read file content"
		every { tool.argsSerializer } returns MultiArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			MultiArgs.File::filePath to "Path to file",
			MultiArgs.File::startLine to "Start line number",
			MultiArgs.File::endLine to "End line number",
			MultiArgs.File::lineNumber to "Show line numbers",
			MultiArgs.Summarize::filePath to "Path to file",
			MultiArgs.Summarize::startLine to "Start line number",
			MultiArgs.Summarize::endLine to "End line number",
			MultiArgs.Summarize::prompt to "Custom prompt",
			MultiArgs.Unicode::filePath to "Path to file",
			MultiArgs.Unicode::maxChars to "Max characters",
		)
		coEvery { tool.describeFunctions() } returns mapOf(
			MultiArgs.File::class to "Read file lines",
			MultiArgs.Summarize::class to "Summarize file content",
			MultiArgs.Unicode::class to "Read unicode characters",
		)
		return tool
	}
	
	// endregion
	
	// region build - simple tool
	
	@Test
	fun `build simple tool returns correct name`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		assertEquals("simple", meta.name)
	}
	
	@Test
	fun `build simple tool returns correct description`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		assertEquals("A simple tool", meta.description)
	}
	
	@Test
	fun `build simple tool has one function named run`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		assertEquals(1, meta.functions.size)
		assertEquals("run", meta.functions[0].name)
	}
	
	@Test
	fun `build simple tool run function uses tool description`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		assertEquals("A simple tool", meta.functions[0].description)
	}
	
	@Test
	fun `build simple tool has all parameters`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		val params = meta.functions[0].parameters
		assertEquals(4, params.size)
		assertNotNull(params["command"])
		assertNotNull(params["timeout_seconds"])
		assertNotNull(params["verbose"])
		assertNotNull(params["tags"])
	}
	
	@Test
	fun `build simple tool required string parameter`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		val command = meta.functions[0].parameters["command"]!!
		assertTrue(command.required)
		assertEquals("The command to execute", command.description)
		assertTrue(command.valueType is ToolMeta.ValueType.StringValue)
	}
	
	@Test
	fun `build simple tool optional integer parameter`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		val timeout = meta.functions[0].parameters["timeout_seconds"]!!
		assertFalse(timeout.required)
		assertEquals("Timeout in seconds, default 60", timeout.description)
		assertTrue(timeout.valueType is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `build simple tool optional boolean parameter`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		val verbose = meta.functions[0].parameters["verbose"]!!
		assertFalse(verbose.required)
		assertEquals("Enable verbose output", verbose.description)
		assertTrue(verbose.valueType is ToolMeta.ValueType.BooleanValue)
	}
	
	@Test
	fun `build simple tool optional array parameter`() = runBlocking {
		val meta = ToolMeta.build(mockSimpleTool())
		val tags = meta.functions[0].parameters["tags"]!!
		assertFalse(tags.required)
		assertEquals("List of tags", tags.description)
		val valueType = tags.valueType
		assertTrue(valueType is ToolMeta.ValueType.ArrayValue)
		assertTrue(valueType.items is ToolMeta.ValueType.StringValue)
	}
	
	// endregion
	
	// region build - sealed class tool
	
	@Test
	fun `build sealed tool returns correct name`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		assertEquals("read", meta.name)
	}
	
	@Test
	fun `build sealed tool has three functions`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		assertEquals(3, meta.functions.size)
	}
	
	@Test
	fun `build sealed tool function names from SerialName`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val names = meta.functions.map { it.name }.toSet()
		assertTrue(names.contains("file"))
		assertTrue(names.contains("summarize"))
		assertTrue(names.contains("unicode"))
	}
	
	@Test
	fun `build sealed tool file function has correct parameters`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val fileFunc = meta.functions.first { it.name == "file" }
		assertEquals(4, fileFunc.parameters.size)
		assertNotNull(fileFunc.parameters["file_path"])
		assertNotNull(fileFunc.parameters["start_line"])
		assertNotNull(fileFunc.parameters["end_line"])
		assertNotNull(fileFunc.parameters["line_number"])
	}
	
	@Test
	fun `build sealed tool file function required parameters`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val fileFunc = meta.functions.first { it.name == "file" }
		assertTrue(fileFunc.parameters["file_path"]!!.required)
		assertTrue(fileFunc.parameters["start_line"]!!.required)
		assertTrue(fileFunc.parameters["end_line"]!!.required)
		assertFalse(fileFunc.parameters["line_number"]!!.required)
	}
	
	@Test
	fun `build sealed tool summarize function has correct parameters`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val summarizeFunc = meta.functions.first { it.name == "summarize" }
		assertEquals(4, summarizeFunc.parameters.size)
		assertNotNull(summarizeFunc.parameters["file_path"])
		assertNotNull(summarizeFunc.parameters["start_line"])
		assertNotNull(summarizeFunc.parameters["end_line"])
		assertNotNull(summarizeFunc.parameters["prompt"])
	}
	
	@Test
	fun `build sealed tool unicode function has correct parameters`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val unicodeFunc = meta.functions.first { it.name == "unicode" }
		assertEquals(2, unicodeFunc.parameters.size)
		assertNotNull(unicodeFunc.parameters["file_path"])
		assertNotNull(unicodeFunc.parameters["max_chars"])
	}
	
	@Test
	fun `build sealed tool descriptions come from describe()`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val fileFunc = meta.functions.first { it.name == "file" }
		assertEquals("Path to file", fileFunc.parameters["file_path"]!!.description)
		assertEquals("Start line number", fileFunc.parameters["start_line"]!!.description)
		assertEquals("End line number", fileFunc.parameters["end_line"]!!.description)
		assertEquals("Show line numbers", fileFunc.parameters["line_number"]!!.description)
	}
	
	// endregion
	
	// region describe() mapping
	
	@Test
	fun `missing description for field throws error`() = runBlocking {
		@Serializable
		data class IncompleteArgs(val a: String, val b: String)
		
		val tool = mockk<Tool<IncompleteArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns IncompleteArgs.serializer()
		coEvery { tool.describe() } returns mapOf(IncompleteArgs::a to "only a")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		assertFailsWith<IllegalStateException> {
			ToolMeta.build(tool)
		}
	}
	
	@Test
	fun `describeFunctions descriptions used for sealed functions`() = runBlocking {
		val meta = ToolMeta.build(mockMultiTool())
		val fileFunc = meta.functions.first { it.name == "file" }
		assertEquals("Read file lines", fileFunc.description)
		val summarizeFunc = meta.functions.first { it.name == "summarize" }
		assertEquals("Summarize file content", summarizeFunc.description)
		val unicodeFunc = meta.functions.first { it.name == "unicode" }
		assertEquals("Read unicode characters", unicodeFunc.description)
	}
	
	// endregion
	
	// region name validation
	
	@Test
	fun `tool name with dash throws error`() = runBlocking {
		@Serializable
		data class Args(val x: String)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "my-tool"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::x to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		assertFailsWith<IllegalArgumentException> {
			ToolMeta.build(tool)
		}
	}
	
	@Test
	fun `tool name without dash is valid`() = runBlocking {
		@Serializable
		data class Args(val x: String)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "valid_name"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::x to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertEquals("valid_name", meta.name)
	}
	
	// endregion
}
