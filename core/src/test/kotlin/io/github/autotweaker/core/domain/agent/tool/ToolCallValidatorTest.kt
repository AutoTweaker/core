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

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator.ValidationResult
import io.mockk.every
import io.mockk.mockk
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
	
	
	private val validator = ToolCallValidator()
	
	// region test data
	
	@Serializable
	private data class SimpleArgs(
		val command: String,
		val timeoutSeconds: Int = 60,
	) : ToolArgs
	
	@Serializable
	private sealed class SealedArgs : ToolArgs {
		@Serializable
		data class File(
			val filePath: String,
			val startLine: Int,
			val endLine: Int,
		) : SealedArgs()
		
		@Serializable
		data class Unicode(
			val filePath: String,
			val maxChars: Int,
		) : SealedArgs()
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockSimpleTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<SimpleArgs>>()
		every { tool.name } returns "bash"
		every { tool.description } returns "Run bash commands"
		every { tool.argsSerializer } returns SimpleArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockSealedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<SealedArgs>>()
		every { tool.name } returns "read"
		every { tool.description } returns "Read files"
		every { tool.argsSerializer } returns SealedArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	// endregion
	
	// region separator
	
	@Test
	fun `valid tool-function name with dash separator`() {
		val result =
			validator.validate("bash-run", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("bash", result.toolName)
	}
	
	@Test
	fun `name without separator fails`() {
		val result =
			validator.validate("bashrun", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `name with only separator fails`() {
		val result = validator.validate("-", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `name with multiple dashes splits on first dash`() {
		val tool = mockk<Tool<SimpleArgs>>()
		every { tool.name } returns "my-tool"
		every { tool.argsSerializer } returns SimpleArgs.serializer()
		val result = validator.validate(
			"my-tool-run", """{"command":"echo","reason":"test"}""", "",
			listOf(tool as Tool<ToolArgs>)
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
	
	// region unknown tool
	
	@Test
	fun `unknown tool name fails`() {
		val result =
			validator.validate("unknown-run", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `unknown function on known tool still succeeds deserialization`() {
		val result =
			validator.validate("bash-unknown", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Success<*>>(result)
	}
	
	// endregion
	
	// region JSON parsing
	
	@Test
	fun `invalid JSON fails`() {
		val result = validator.validate("bash-run", "not json", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `JSON array fails`() {
		val result = validator.validate("bash-run", "[1,2,3]", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `empty JSON object fails missing reason`() {
		val result = validator.validate("bash-run", "{}", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
	
	// region reason field
	
	@Test
	fun `missing reason field fails`() {
		val result = validator.validate("bash-run", """{"command":"echo"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `reason is extracted correctly`() {
		val result =
			validator.validate("bash-run", """{"command":"echo","reason":"testing"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("testing", result.reason)
	}
	
	@Test
	fun `reason is not passed to args deserialization`() {
		val result =
			validator.validate("bash-run", """{"command":"echo","reason":"testing"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs
		assertEquals("echo", args.command)
	}
	
	// endregion
	
	// region simple args deserialization
	
	@Test
	fun `deserializes simple args with all fields`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo hello","timeout_seconds":30,"reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs
		assertEquals("echo hello", args.command)
		assertEquals(30, args.timeoutSeconds)
	}
	
	@Test
	fun `deserializes simple args with defaults`() {
		val result =
			validator.validate("bash-run", """{"command":"echo","reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs
		assertEquals("echo", args.command)
		assertEquals(60, args.timeoutSeconds)
	}
	
	@Test
	fun `camelCase JSON keys fails with snake_case strategy`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","timeoutSeconds":30,"reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `snake_case JSON keys work directly`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","timeout_seconds":15,"reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SimpleArgs
		assertEquals(15, args.timeoutSeconds)
	}
	
	@Test
	fun `missing required field fails deserialization`() {
		val result =
			validator.validate("bash-run", """{"timeout_seconds":30,"reason":"test"}""", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `wrong type for field fails deserialization`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","timeout_seconds":"not_a_number","reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
	
	// region sealed args deserialization
	
	@Test
	fun `deserializes sealed args with type injection`() {
		val result = validator.validate(
			"read-file",
			"""{"file_path":"/tmp/test.txt","start_line":1,"end_line":10,"reason":"read file"}""", "",
			listOf(mockSealedTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as SealedArgs.File
		assertEquals("/tmp/test.txt", args.filePath)
		assertEquals(1, args.startLine)
		assertEquals(10, args.endLine)
	}
	
	@Test
	fun `sealed args function name used as type discriminator`() {
		val result = validator.validate(
			"read-unicode",
			"""{"file_path":"/tmp/test.txt","max_chars":1000,"reason":"read unicode"}""", "",
			listOf(mockSealedTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertIs<SealedArgs.Unicode>(result.args)
	}
	
	@Test
	fun `sealed args with wrong type value fails`() {
		val result = validator.validate(
			"read-nonexistent",
			"""{"file_path":"/tmp/test.txt","start_line":1,"end_line":10,"reason":"test"}""", "",
			listOf(mockSealedTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `sealed args camelCase keys fails with snake_case strategy`() {
		val result = validator.validate(
			"read-file",
			"""{"filePath":"/tmp/test.txt","startLine":1,"endLine":10,"reason":"read"}""", "",
			listOf(mockSealedTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
	
	// region multiple tools
	
	@Test
	fun `validator with multiple tools routes correctly`() {
		val tools = listOf(mockSimpleTool(), mockSealedTool())
		
		val bashResult = validator.validate("bash-run", """{"command":"echo","reason":"test"}""", "", tools)
		assertIs<ValidationResult.Success<*>>(bashResult)
		assertEquals("bash", bashResult.toolName)
		
		val readResult = validator.validate(
			"read-file",
			"""{"file_path":"/tmp/test.txt","start_line":1,"end_line":5,"reason":"read"}""", "",
			tools
		)
		assertIs<ValidationResult.Success<*>>(readResult)
		assertEquals("read", readResult.toolName)
	}
	
	@Test
	fun `validator with empty tool list fails all`() {
		val result = validator.validate("bash-run", """{"command":"echo","reason":"test"}""", "", emptyList())
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
	
	// region callId logging
	
	@Test
	fun `callId is not used in result but does not affect validation`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","reason":"test"}""",
			callId = "my-call-123",
			tools = listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Success<*>>(result)
	}
	
	// endregion
	
	// region edge cases
	
	@Test
	fun `empty arguments string fails JSON parse`() {
		val result = validator.validate("bash-run", "", "", listOf(mockSimpleTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `extra unknown fields in args fails`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":"echo","unknown_field":"value","reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `args with nested object fails for simple string field`() {
		val result = validator.validate(
			"bash-run",
			"""{"command":{"nested":"object"},"reason":"test"}""", "",
			listOf(mockSimpleTool())
		)
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
}
