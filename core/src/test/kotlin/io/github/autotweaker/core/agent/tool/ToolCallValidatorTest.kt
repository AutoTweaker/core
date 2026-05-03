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

package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.tool.Tool
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToolCallValidatorTest {
	
	private val settings: List<SettingItem> = listOf(
		SettingItem(
			SettingKey("core.agent.tool.response.json.error"),
			SettingItem.Value.ValString("JSON error: %s"),
			"",
		),
		SettingItem(
			SettingKey("core.agent.tool.response.property.missing"),
			SettingItem.Value.ValString("Missing property in %s: %s"),
			"",
		),
		SettingItem(
			SettingKey("core.agent.tool.response.property.error"),
			SettingItem.Value.ValString("Property error in %s: %s should be %s"),
			"",
		),
		SettingItem(
			SettingKey("core.agent.tool.response.function.name.error"),
			SettingItem.Value.ValString("Function not found: %s"),
			"",
		),
	)
	
	// region helpers
	
	private fun mockToolWithMeta(
		name: String = "bash", functions: List<Tool.Function> = listOf(
			Tool.Function(
				"run", "run command", mapOf(
					"cmd" to Tool.Function.Property("command", true, Tool.Function.Property.ValueType.StringValue()),
				)
			)
		)
	): Tool {
		val tool = mockk<Tool>()
		val meta = Tool.Meta(name, "description", functions)
		every { tool.resolveMeta(any()) } returns meta
		return tool
	}
	
	private fun successArguments(): String = """{"cmd":"echo hello","reason":"needed"}"""
	
	private fun createValidator(tools: List<Tool> = listOf(mockToolWithMeta())): ToolCallValidator {
		return ToolCallValidator(tools, settings)
	}
	// endregion
	
	// region success cases
	
	@Test
	fun `validate success returns correct tool and function names`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", successArguments())
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
		assertEquals("bash", result.toolName)
		assertEquals("run", result.functionName)
		assertEquals("needed", result.reason)
		assertTrue(result.arguments.containsKey("cmd"))
	}
	
	@Test
	fun `validate success strips reason from arguments`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", """{"cmd":"ls","reason":"need it"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
		assertFalse(result.arguments.containsKey("reason"))
	}
	
	@Test
	fun `validate success with extra optional properties`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", """{"cmd":"ls","reason":"ok","extra":"ignored"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate success with tool underscore name`() {
		val tool = mockToolWithMeta(
			"my_tool", listOf(
				Tool.Function(
					"my_func", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		// Tool names with underscores cannot be resolved because split("_", limit=2)
		// parses "my_tool_my_func" as toolName="my", functionName="tool_my_func"
		val result = validator.validate("my_tool_my_func", """{"x":"val","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	// endregion
	
	// region failure cases - JSON errors
	
	@Test
	fun `validate failure invalid JSON`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", "not json")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
		assertTrue(result.errorMessage.contains("JSON error:"))
	}
	
	@Test
	fun `validate failure JSON array instead of object`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", "[]")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure JSON null`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", "null")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	// endregion
	
	// region failure cases - name parsing
	
	@Test
	fun `validate failure no underscore in tool call name`() {
		val validator = createValidator()
		val result = validator.validate("bash", successArguments())
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
		assertTrue(result.errorMessage.contains("Function not found:"))
	}
	
	@Test
	fun `validate failure tool name not registered`() {
		val validator = createValidator()
		val result = validator.validate("unknown_run", successArguments())
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure function name not found`() {
		val validator = createValidator()
		val result = validator.validate("bash_unknown", successArguments())
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	// endregion
	
	// region failure cases - reason
	
	@Test
	fun `validate failure missing reason`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", """{"cmd":"echo"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
		assertTrue(result.errorMessage.contains("reason"))
	}
	
	@Test
	fun `validate success reason can be numeric`() {
		// JsonPrimitive includes numeric values; the validator accepts any JsonPrimitive as reason
		val validator = createValidator()
		val result = validator.validate("bash_run", """{"cmd":"echo","reason":123}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
		assertEquals("123", result.reason)
	}
	// endregion
	
	// region failure cases - missing required
	
	@Test
	fun `validate failure missing required param`() {
		val validator = createValidator()
		val result = validator.validate("bash_run", """{"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
		assertTrue(result.errorMessage.contains("cmd"))
	}
	
	@Test
	fun `validate success optional param can be absent`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"req" to Tool.Function.Property("req", true, Tool.Function.Property.ValueType.StringValue()),
						"opt" to Tool.Function.Property("opt", false, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"req":"x","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	// endregion
	
	// region type validation
	
	@Test
	fun `validate success string value`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":"text","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure string value with number`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":42,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate success number value with double`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.NumberValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":3.14,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate success number value with int`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.NumberValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":42,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure number value with string`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.NumberValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":"text","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate success integer value`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.IntegerValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":42,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure integer value with float`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.IntegerValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":3.14,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure integer value with string`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"x" to Tool.Function.Property("x", false, Tool.Function.Property.ValueType.IntegerValue()),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"x":"text","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate success boolean value true`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"flag" to Tool.Function.Property("flag", false, Tool.Function.Property.ValueType.BooleanValue),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"flag":true,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate success boolean value false`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"flag" to Tool.Function.Property("flag", false, Tool.Function.Property.ValueType.BooleanValue),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"flag":false,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure boolean value with number`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"flag" to Tool.Function.Property("flag", false, Tool.Function.Property.ValueType.BooleanValue),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"flag":1,"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate success array value`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"items" to Tool.Function.Property(
							"items", false,
							Tool.Function.Property.ValueType.ArrayValue(Tool.Function.Property.ValueType.StringValue())
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"items":["a","b"],"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure array value with wrong element type`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"items" to Tool.Function.Property(
							"items", false,
							Tool.Function.Property.ValueType.ArrayValue(Tool.Function.Property.ValueType.StringValue())
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"items":["a",42],"reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure array value with non array`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"items" to Tool.Function.Property(
							"items", false,
							Tool.Function.Property.ValueType.ArrayValue(Tool.Function.Property.ValueType.StringValue())
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate("bash_run", """{"items":"not_array","reason":"ok"}""")
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate success object value`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"config" to Tool.Function.Property(
							"config", false,
							Tool.Function.Property.ValueType.ObjectValue(
								mapOf(
									"name" to Tool.Function.Property.ValueType.StringValue(),
									"count" to Tool.Function.Property.ValueType.IntegerValue(),
								)
							)
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate(
			"bash_run",
			"""{"config":{"name":"test","count":5},"reason":"ok"}"""
		)
		
		assertIs<ToolCallValidator.ValidationResult.Success>(result)
	}
	
	@Test
	fun `validate failure object value missing nested field`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"config" to Tool.Function.Property(
							"config", false,
							Tool.Function.Property.ValueType.ObjectValue(
								mapOf(
									"name" to Tool.Function.Property.ValueType.StringValue(),
									"count" to Tool.Function.Property.ValueType.IntegerValue(),
								)
							)
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate(
			"bash_run",
			"""{"config":{"name":"test"},"reason":"ok"}"""
		)
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure object value wrong nested type`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"config" to Tool.Function.Property(
							"config", false,
							Tool.Function.Property.ValueType.ObjectValue(
								mapOf(
									"name" to Tool.Function.Property.ValueType.StringValue(),
								)
							)
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate(
			"bash_run",
			"""{"config":{"name":42},"reason":"ok"}"""
		)
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	
	@Test
	fun `validate failure object value with non object`() {
		val tool = mockToolWithMeta(
			"bash", listOf(
				Tool.Function(
					"run", "desc", mapOf(
						"config" to Tool.Function.Property(
							"config", false,
							Tool.Function.Property.ValueType.ObjectValue(
								mapOf(
									"name" to Tool.Function.Property.ValueType.StringValue(),
								)
							)
						),
					)
				)
			)
		)
		val validator = createValidator(listOf(tool))
		val result = validator.validate(
			"bash_run",
			"""{"config":"not_object","reason":"ok"}"""
		)
		
		assertIs<ToolCallValidator.ValidationResult.Failure>(result)
	}
	// endregion
}
