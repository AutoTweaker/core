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

package io.github.autotweaker.core.domain.agent.tool

import io.github.autotweaker.api.discard
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.tool.ToolCallParser.ValidationResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ToolCallValidatorTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val validator = ToolCallParser()
	
	// region test data
	
	@Serializable
	private sealed class SimpleArgs : ToolArgs {
		@Serializable
		@SerialName("run")
		data class Run(
			@SerialName("command") val command: String,
			@SerialName("timeout_seconds") val timeoutSeconds: Int = 60,
		) : SimpleArgs()
	}
	
	@Serializable
	sealed class SealedArgs : ToolArgs {
		@Serializable
		@SerialName("file")
		data class File(
			@SerialName("file_path") val filePath: String,
			@SerialName("start_line") val startLine: Int,
			@SerialName("end_line") val endLine: Int,
		) : SealedArgs()
		
		@Serializable
		@SerialName("unicode")
		data class Unicode(
			@SerialName("file_path") val filePath: String,
			@SerialName("max_chars") val maxChars: Int,
		) : SealedArgs()
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockSimpleTool(): Tool<*> {
		val tool = mockk<Tool<SimpleArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"bash", "Run bash commands", listOf(
					ToolMeta.Function("run", "Run bash commands", emptyList())
				)
			), SimpleArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockSealedTool(): Tool<*> {
		val tool = mockk<Tool<SealedArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"read", "Read files", listOf(
					ToolMeta.Function("file", "Read file lines", emptyList()),
					ToolMeta.Function("unicode", "Read unicode chars", emptyList()),
				)
			), SealedArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private suspend fun toolMetaCache(vararg tools: Tool<*>): MetaCache =
		Tools.cacheMeta(tools.associate { it.meta().first.name to it as Tool<ToolArgs> })
	
	// endregion
	
	// region separator
	
	@Test
	fun `valid tool-function name with dash separator`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("bash", result.toolName)
	}
	
	@Test
	fun `name without separator fails`() = runBlocking {
		val result = validator.validate(
			"bashrun", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `name with only separator fails`() = runBlocking {
		val result = validator.validate(
			"-", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `name with multiple dashes splits on first dash`() = runBlocking {
		val tool = mockk<Tool<SimpleArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"my-tool", "", listOf(
					ToolMeta.Function("run", "", emptyList())
				)
			), SimpleArgs.serializer()
		)
		val result = validator.validate(
			"my-tool-run", """{"command":"echo","reason":"tests"}""", "",
			toolMetaCache(tool as Tool<*>),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region unknown tool
	
	@Test
	fun `unknown tool name fails`() = runBlocking {
		val result = validator.validate(
			"unknown-run", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `unknown function on known tool fails deserialization`() = runBlocking {
		val result = validator.validate(
			"bash-unknown", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region JSON parsing
	
	@Test
	fun `invalid JSON fails`() = runBlocking {
		val result = validator.validate("bash-run", "not json", "", toolMetaCache(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `JSON array fails`() = runBlocking {
		val result = validator.validate("bash-run", "[1,2,3]", "", toolMetaCache(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `empty JSON object fails missing reason`() = runBlocking {
		val result = validator.validate("bash-run", "{}", "", toolMetaCache(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region reason field
	
	@Test
	fun `missing reason field fails`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `reason is extracted correctly`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","reason":"testing"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("testing", result.reason)
	}
	
	@Test
	fun `reason is not passed to args deserialization`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","reason":"testing"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs.Run
		assertEquals("echo", args.command)
	}
	
	// endregion
	
	// region simple args deserialization
	
	@Test
	fun `deserializes simple args with all fields`() = runBlocking {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo hello","timeout_seconds":30,"reason":"tests"}""",
			"",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs.Run
		assertEquals("echo hello", args.command)
		assertEquals(30, args.timeoutSeconds)
	}
	
	@Test
	fun `deserializes simple args with defaults`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs.Run
		assertEquals("echo", args.command)
		assertEquals(60, args.timeoutSeconds)
	}
	
	@Test
	fun `camelCase JSON keys fails with snake_case strategy`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","timeoutSeconds":30,"reason":"tests"}""", "",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `snake_case JSON keys work directly`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","timeout_seconds":15,"reason":"tests"}""", "",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs.Run
		assertEquals(15, args.timeoutSeconds)
	}
	
	@Test
	fun `missing required field fails deserialization`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"timeout_seconds":30,"reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `wrong type for field fails deserialization`() = runBlocking {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","timeout_seconds":"not_a_number","reason":"tests"}""",
			"",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region sealed args deserialization
	
	@Test
	fun `deserializes sealed args with type injection`() = runBlocking {
		val result = validator.validate(
			"read-file",
			"""{"file_path":"/tmp/test.txt","start_line":1,"end_line":10,"reason":"read file"}""",
			"",
			toolMetaCache(mockSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SealedArgs.File
		assertEquals("/tmp/test.txt", args.filePath)
		assertEquals(1, args.startLine)
		assertEquals(10, args.endLine)
	}
	
	@Suppress("EXPOSED_FROM_PRIVATE_IN_CLASS")
	@Test
	fun `sealed args function name used as type discriminator`() = runBlocking {
		val result = validator.validate(
			"read-unicode",
			"""{"file_path":"/tmp/test.txt","max_chars":1000,"reason":"read unicode"}""",
			"",
			toolMetaCache(mockSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertIs<SealedArgs.Unicode>(result.args)
	}.discard()
	
	@Test
	fun `sealed args with wrong type value fails`() = runBlocking {
		val result = validator.validate(
			"read-nonexistent",
			"""{"file_path":"/tmp/test.txt","start_line":1,"end_line":10,"reason":"tests"}""",
			"",
			toolMetaCache(mockSealedTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `sealed args camelCase keys fails with snake_case strategy`() = runBlocking {
		val result = validator.validate(
			"read-file",
			"""{"filePath":"/tmp/test.txt","startLine":1,"endLine":10,"reason":"reads"}""",
			"",
			toolMetaCache(mockSealedTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region multiple tools
	
	@Test
	fun `validator with multiple tools routes correctly`() = runBlocking {
		val cache = toolMetaCache(mockSimpleTool(), mockSealedTool())
		
		val bashResult = validator.validate("bash-run", """{"command":"echo","reason":"tests"}""", "", cache)
		assertIs<ValidationResult.Success<*>>(bashResult)
		assertEquals("bash", bashResult.toolName)
		
		val readResult = validator.validate(
			"read-file", """{"file_path":"/tmp/test.txt","start_line":1,"end_line":5,"reason":"reads"}""", "", cache
		)
		assertIs<ValidationResult.Success<*>>(readResult)
		assertEquals("read", readResult.toolName)
	}
	
	@Test
	fun `validator with empty tool list fails all`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","reason":"tests"}""", "", emptyMap()
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
	
	// region callId logging
	
	@Test
	fun `callId is not used in result but does not affect validation`() = runBlocking {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","reason":"tests"}""",
			"my-call-123",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
	}.discard()
	
	// endregion
	
	// region edge cases
	
	@Test
	fun `empty arguments string fails JSON parse`() = runBlocking {
		val result = validator.validate("bash-run", "", "", toolMetaCache(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `extra unknown fields in args fails`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":"echo","unknown_field":"value","reason":"tests"}""", "",
			toolMetaCache(mockSimpleTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `args with nested object fails for simple string field`() = runBlocking {
		val result = validator.validate(
			"bash-run", """{"command":{"nested":"object"},"reason":"tests"}""", "", toolMetaCache(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
}
