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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class ToolAssemblerTest {
	
	private val reasonDesc = "The reason you are calling this function"
	private val defaultSettings: List<SettingItem> = listOf(
		SettingItem(
			SettingKey("core.agent.tool.description.reason"),
			SettingItem.Value.ValString(reasonDesc),
			"",
		)
	)
	
	// region helpers
	
	private fun mockTool(
		name: String,
		description: String = "A test tool",
		functions: List<Tool.Function>,
	): Tool {
		val tool = mockk<Tool>()
		val meta = Tool.Meta(name, description, functions)
		every { tool.resolveMeta(any()) } returns meta
		return tool
	}
	
	private fun stringProp(description: String, required: Boolean = false, enum: List<String>? = null) =
		Tool.Function.Property(description, required, Tool.Function.Property.ValueType.StringValue(enum))
	
	private fun numberProp(description: String, required: Boolean = false, enum: List<Double>? = null) =
		Tool.Function.Property(description, required, Tool.Function.Property.ValueType.NumberValue(enum))
	
	private fun integerProp(description: String, required: Boolean = false, enum: List<Int>? = null) =
		Tool.Function.Property(description, required, Tool.Function.Property.ValueType.IntegerValue(enum))
	
	private fun boolProp(description: String, required: Boolean = false) =
		Tool.Function.Property(description, required, Tool.Function.Property.ValueType.BooleanValue)
	
	private fun arrayProp(description: String, items: Tool.Function.Property.ValueType, required: Boolean = false) =
		Tool.Function.Property(description, required, Tool.Function.Property.ValueType.ArrayValue(items))
	
	private fun objectProp(
		description: String,
		properties: Map<String, Tool.Function.Property.ValueType>,
		required: Boolean = false,
	) = Tool.Function.Property(description, required, Tool.Function.Property.ValueType.ObjectValue(properties))
	// endregion
	
	@Test
	fun `assemble returns null for empty tools`() {
		val result = ToolAssembler.assemble(emptyList(), defaultSettings)
		assertNull(result)
	}
	
	@Test
	fun `assemble single tool single function`() {
		val tool = mockTool(
			"bash", functions = listOf(
				Tool.Function(
					"run", "Run a bash command", mapOf(
						"command" to stringProp("The command", required = true),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		assertNotNull(result)
		assertEquals(1, result.size)
		assertEquals("bash_run", result[0].name)
		assertEquals("Run a bash command", result[0].description)
		val params = result[0].parameters.jsonObject
		assertEquals("object", params["type"]?.jsonPrimitive?.content)
		val props = params["properties"]!!.jsonObject
		assertTrue(props.containsKey("command"))
		assertTrue(props.containsKey("reason"))
		val required = params["required"]!!.jsonArray
		assertTrue(required.any { it.jsonPrimitive.content == "command" })
		assertTrue(required.any { it.jsonPrimitive.content == "reason" })
	}
	
	@Test
	fun `assemble single tool multiple functions`() {
		val tool = mockTool(
			"read", functions = listOf(
				Tool.Function(
					"file", "Read a file", mapOf(
						"path" to stringProp("File path"),
					)
				),
				Tool.Function(
					"summarize", "Summarize content", mapOf(
						"content" to stringProp("Content to summarize"),
					)
				),
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		assertNotNull(result)
		assertEquals(2, result.size)
		assertEquals("read_file", result[0].name)
		assertEquals("read_summarize", result[1].name)
	}
	
	@Test
	fun `assemble multiple tools`() {
		val t1 = mockTool(
			"bash", functions = listOf(
				Tool.Function("run", "Run a command", emptyMap())
			)
		)
		val t2 = mockTool(
			"read", functions = listOf(
				Tool.Function("file", "Read a file", emptyMap())
			)
		)
		val result = ToolAssembler.assemble(listOf(t1, t2), defaultSettings)
		assertNotNull(result)
		assertEquals(2, result.size)
		assertEquals("bash_run", result[0].name)
		assertEquals("read_file", result[1].name)
	}
	
	@Test
	fun `assemble string property with enum`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"action", "Do something", mapOf(
						"mode" to stringProp("Mode", enum = listOf("a", "b")),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val modeProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["mode"]!!.jsonObject
		assertEquals("string", modeProp["type"]?.jsonPrimitive?.content)
		assertEquals(listOf("a", "b"), modeProp["enum"]?.jsonArray?.map { it.jsonPrimitive.content })
	}
	
	@Test
	fun `assemble number property`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"calc", "Calculate", mapOf(
						"value" to numberProp("A number"),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val valProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["value"]!!.jsonObject
		assertEquals("number", valProp["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `assemble number property with enum`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"calc", "Calculate", mapOf(
						"value" to numberProp("A number", enum = listOf(1.0, 2.5)),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val valProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["value"]!!.jsonObject
		assertEquals("number", valProp["type"]?.jsonPrimitive?.content)
		val enumArr = valProp["enum"]?.jsonArray
		assertNotNull(enumArr)
		assertEquals(2, enumArr.size)
	}
	
	@Test
	fun `assemble integer property`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"count", "Count things", mapOf(
						"n" to integerProp("An integer"),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val nProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["n"]!!.jsonObject
		assertEquals("integer", nProp["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `assemble integer property with enum`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"count", "Count things", mapOf(
						"n" to integerProp("An integer", enum = listOf(1, 2, 3)),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val nProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["n"]!!.jsonObject
		assertEquals("integer", nProp["type"]?.jsonPrimitive?.content)
		assertNotNull(nProp["enum"])
	}
	
	@Test
	fun `assemble boolean property`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"toggle", "Toggle something", mapOf(
						"flag" to boolProp("A flag"),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val flagProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["flag"]!!.jsonObject
		assertEquals("boolean", flagProp["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `assemble array property`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"list", "List items", mapOf(
						"items" to arrayProp("Items", Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val itemsProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["items"]!!.jsonObject
		assertEquals("array", itemsProp["type"]?.jsonPrimitive?.content)
		val itemsType = itemsProp["items"]?.jsonObject
		assertEquals("string", itemsType?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	fun `assemble object property`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"nested", "Nested data", mapOf(
						"config" to objectProp(
							"Config", mapOf(
								"name" to Tool.Function.Property.ValueType.StringValue(),
								"count" to Tool.Function.Property.ValueType.IntegerValue(),
							)
						),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val configProp = result!![0].parameters.jsonObject["properties"]!!.jsonObject["config"]!!.jsonObject
		assertEquals("object", configProp["type"]?.jsonPrimitive?.content)
		val nestedProps = configProp["properties"]?.jsonObject
		assertNotNull(nestedProps)
		assertEquals("string", nestedProps["name"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
		assertEquals("integer", nestedProps["count"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	fun `assemble reason field always in properties and required`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"test", "Test", mapOf(
						"opt" to stringProp("Optional param", required = false),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val params = result!![0].parameters.jsonObject
		val props = params["properties"]!!.jsonObject
		val required = params["required"]!!.jsonArray
		
		assertTrue(props.containsKey("reason"))
		assertEquals("string", props["reason"]!!.jsonObject["type"]!!.jsonPrimitive.content)
		assertEquals(reasonDesc, props["reason"]!!.jsonObject["description"]!!.jsonPrimitive.content)
		assertTrue(required.any { it.jsonPrimitive.content == "reason" })
	}
	
	@Test
	fun `assemble required param listed but optional not`() {
		val tool = mockTool(
			"tool", functions = listOf(
				Tool.Function(
					"test", "Test", mapOf(
						"opt" to stringProp("Optional", required = false),
						"req" to stringProp("Required", required = true),
					)
				)
			)
		)
		val result = ToolAssembler.assemble(listOf(tool), defaultSettings)
		val required = result!![0].parameters.jsonObject["required"]!!.jsonArray
		val names = required.map { it.jsonPrimitive.content }.toSet()
		assertTrue(names.contains("req"))
		assertFalse(names.contains("opt"))
		assertTrue(names.contains("reason"))
	}
}
