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

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.core.TestServices
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class ToolAssemblerTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region test data
	
	@Serializable
	private data class SimpleArgs(
		val command: String,
		val timeoutSeconds: Int = 60,
	) : ToolArgs
	
	@Serializable
	private sealed class MultiArgs : ToolArgs {
		@Serializable
		data class File(
			val filePath: String,
			val startLine: Int,
		) : MultiArgs()
		
		@Serializable
		data class Unicode(
			val filePath: String,
			val maxChars: Int,
		) : MultiArgs()
	}
	
	@Serializable
	private sealed class Inner : ToolArgs {
		@Serializable
		data class A(val x: Int) : Inner()
		
		@Serializable
		data class B(val y: String) : Inner()
	}
	
	@Serializable
	private data class Nested(val a: String, val b: Int) : ToolArgs
	
	@Suppress("UNCHECKED_CAST")
	private fun mockSimpleTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<SimpleArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"bash", "Run bash commands", listOf(
					ToolMeta.Function(
						"run", "Run bash commands", listOf(
							ToolMeta.Prop("command", ToolMeta.Type.TString, true, "The command"),
							ToolMeta.Prop("timeout_seconds", ToolMeta.Type.TInt, false, "Timeout in seconds"),
						)
					)
				)
			),
			SimpleArgs.serializer()
		)
		return tool as Tool<ToolArgs>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockMultiTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<MultiArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"read", "Read files", listOf(
					ToolMeta.Function(
						"file", "Read file lines", listOf(
							ToolMeta.Prop("file_path", ToolMeta.Type.TString, true, "File path"),
							ToolMeta.Prop("start_line", ToolMeta.Type.TInt, true, "Start line"),
						)
					),
					ToolMeta.Function(
						"unicode", "Read unicode chars", listOf(
							ToolMeta.Prop("file_path", ToolMeta.Type.TString, true, "File path"),
							ToolMeta.Prop("max_chars", ToolMeta.Type.TInt, true, "Max chars"),
						)
					),
				)
			),
			MultiArgs.serializer()
		)
		return tool as Tool<ToolArgs>
	}
	
	private fun assemble(tools: List<Tool<ToolArgs>>) = runBlocking {
		val metaCache = Tools.cacheMeta(tools.associate { it.meta().first.name to it })
		ToolAssembler.assemble(metaCache) { true }
	}
	
	private fun assembleWithTool(tool: Tool<ToolArgs>) = runBlocking {
		val metaCache = Tools.cacheMeta(mapOf(tool.meta().first.name to tool))
		ToolAssembler.assemble(metaCache) { true }
	}
	
	// endregion
	
	// region basic assembly
	
	@Test
	fun `empty tools returns null`() = runBlocking {
		val result = ToolAssembler.assemble(emptyMap()) { true }
		assertNull(result)
	}
	
	@Test
	fun `single simple tool produces one entry`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		assertNotNull(result)
		assertEquals(1, result.size)
	}
	
	@Test
	fun `simple tool entry name is tool-run`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		assertEquals("bash-run", result!![0].name)
	}
	
	@Test
	fun `simple tool entry description is function description`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		assertEquals("Run bash commands", result!![0].description)
	}
	
	// endregion
	
	// region sealed tool assembly
	
	@Test
	fun `sealed tool produces one entry per subclass`() = runBlocking {
		val result = assemble(listOf(mockMultiTool()))
		assertNotNull(result)
		assertEquals(2, result.size)
	}
	
	@Test
	fun `sealed tool entries have correct names`() = runBlocking {
		val result = assemble(listOf(mockMultiTool()))
		val names = result!!.map { it.name }.toSet()
		assertTrue(names.contains("read-file"))
		assertTrue(names.contains("read-unicode"))
	}
	
	@Test
	fun `sealed tool entries have correct descriptions`() = runBlocking {
		val result = assemble(listOf(mockMultiTool()))
		val fileEntry = result!!.first { it.name == "read-file" }
		assertEquals("Read file lines", fileEntry.description)
		val unicodeEntry = result.first { it.name == "read-unicode" }
		assertEquals("Read unicode chars", unicodeEntry.description)
	}
	
	// endregion
	
	// region parameters structure
	
	@Test
	fun `parameters has type object`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val params = result!![0].parameters.jsonObject
		assertEquals("object", params["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `parameters has properties`() {
		runBlocking {
			val result = assemble(listOf(mockSimpleTool()))
			val params = result!![0].parameters.jsonObject
			val props = params["properties"]?.jsonObject
			assertNotNull(props)
		}
	}
	
	@Test
	fun `parameters properties contain tool fields`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		assertTrue(props.containsKey("command"))
		assertTrue(props.containsKey("timeout_seconds"))
	}
	
	@Test
	fun `parameters properties contain reason field`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		assertTrue(props.containsKey("reason"))
	}
	
	@Test
	fun `parameters required contains required tool fields`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val required = result!![0].parameters.jsonObject["required"]?.jsonArray
		assertNotNull(required)
		val requiredNames = required.map { it.jsonPrimitive.content }
		assertTrue(requiredNames.contains("command"))
	}
	
	@Test
	fun `parameters required always contains reason`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val required = result!![0].parameters.jsonObject["required"]?.jsonArray
		val requiredNames = required!!.map { it.jsonPrimitive.content }
		assertTrue(requiredNames.contains("reason"))
	}
	
	@Test
	fun `parameters required does not contain optional fields`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val required = result!![0].parameters.jsonObject["required"]?.jsonArray
		val requiredNames = required!!.map { it.jsonPrimitive.content }
		assertFalse(requiredNames.contains("timeout_seconds"))
	}
	
	// endregion
	
	// region property types in JSON
	
	@Test
	fun `string property has type string`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val command = props["command"]?.jsonObject!!
		assertEquals("string", command["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `string property has description`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val command = props["command"]?.jsonObject!!
		assertEquals("The command", command["description"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `integer property has type integer`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val timeout = props["timeout_seconds"]?.jsonObject!!
		assertEquals("integer", timeout["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	fun `reason property has type string`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool()))
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val reason = props["reason"]?.jsonObject!!
		assertEquals("string", reason["type"]?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `boolean property has type boolean`() = runBlocking {
		@Serializable
		data class Args(val flag: Boolean) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop("flag", ToolMeta.Type.TBoolean, true, "desc")
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		assertEquals("boolean", props["flag"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `array property has type array with items`() = runBlocking {
		@Serializable
		data class Args(val items: List<String>) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop("items", ToolMeta.Type.TList(ToolMeta.Type.TString), true, "desc")
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val items = props["items"]?.jsonObject!!
		assertEquals("array", items["type"]?.jsonPrimitive?.content)
		assertEquals("string", items["items"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `map property has type object with additionalProperties`() = runBlocking {
		@Serializable
		data class Args(val config: Map<String, Int>) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop(
								"config",
								ToolMeta.Type.TMap(ToolMeta.Type.TInt),
								true,
								"desc"
							)
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val config = props["config"]?.jsonObject!!
		assertEquals("object", config["type"]?.jsonPrimitive?.content)
		assertEquals("integer", config["additionalProperties"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `nested sealed property produces oneOf schema`() = runBlocking {
		@Serializable
		data class Args(val inner: Inner) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop(
								"inner", ToolMeta.Type.OneOf(
									"inner", listOf(
										ToolMeta.Type.OneOf.Variant(
											"a", "", listOf(
												ToolMeta.Prop("x", ToolMeta.Type.TInt, true, "")
											)
										),
										ToolMeta.Type.OneOf.Variant(
											"b", "", listOf(
												ToolMeta.Prop("y", ToolMeta.Type.TString, true, "")
											)
										),
									)
								), true, "desc"
							)
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val inner = props["inner"]?.jsonObject!!
		assertEquals("object", inner["type"]?.jsonPrimitive?.content)
		val oneOf = inner["oneOf"]?.jsonArray!!
		assertEquals(2, oneOf.size)
		val variantAEntry = oneOf[0].jsonObject
		assertEquals("object", variantAEntry["type"]?.jsonPrimitive?.content)
		val requiredFields = variantAEntry["required"]?.jsonArray?.map { it.jsonPrimitive.content }!!
		assertTrue(requiredFields.contains("type"))
		val variantAProps = variantAEntry["properties"]?.jsonObject!!
		assertEquals("a", variantAProps["type"]?.jsonObject?.get("const")?.jsonPrimitive?.content)
		assertEquals("integer", variantAProps["x"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `number property has type number`() = runBlocking {
		@Serializable
		data class Args(val ratio: Double) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop("ratio", ToolMeta.Type.TDouble, true, "desc")
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		assertEquals("number", props["ratio"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `object property has type object with properties`() = runBlocking {
		@Serializable
		data class Args(val nested: Nested) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop(
								"nested", ToolMeta.Type.Obj(
									"nested", listOf(
										ToolMeta.Prop("a", ToolMeta.Type.TString, true, ""),
										ToolMeta.Prop("b", ToolMeta.Type.TInt, true, ""),
									)
								), true, "desc"
							)
						)
					)
				)
			),
			Args.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val props = result!![0].parameters.jsonObject["properties"]?.jsonObject!!
		val nested = props["nested"]?.jsonObject!!
		assertEquals("object", nested["type"]?.jsonPrimitive?.content)
		val innerProps = nested["properties"]?.jsonObject!!
		assertEquals("string", innerProps["a"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
		assertEquals("integer", innerProps["b"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
	}
	
	// endregion
	
	// region multiple tools
	
	@Test
	fun `multiple tools produce combined entries`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool(), mockMultiTool()))
		assertNotNull(result)
		assertEquals(3, result.size)
	}
	
	@Test
	fun `multiple tools have correct names`() = runBlocking {
		val result = assemble(listOf(mockSimpleTool(), mockMultiTool()))
		val names = result!!.map { it.name }.toSet()
		assertTrue(names.contains("bash-run"))
		assertTrue(names.contains("read-file"))
		assertTrue(names.contains("read-unicode"))
	}
	
	// endregion
	
	// region edge cases
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `tool with no required fields still has reason in required`() = runBlocking {
		@Serializable
		data class AllOptional(val x: String = "default") : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop("x", ToolMeta.Type.TString, false, "desc")
						)
					)
				)
			),
			AllOptional.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val required = result!![0].parameters.jsonObject["required"]?.jsonArray
		val requiredNames = required!!.map { it.jsonPrimitive.content }
		assertEquals(1, requiredNames.size)
		assertTrue(requiredNames.contains("reason"))
	}
	
	@Test
	@Suppress("UNCHECKED_CAST")
	fun `tool with all required fields includes all plus reason`() = runBlocking {
		@Serializable
		data class AllRequired(val a: String, val b: Int) : ToolArgs
		
		val tool = mockk<Tool<ToolArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"t", "d", listOf(
					ToolMeta.Function(
						"run", "d", listOf(
							ToolMeta.Prop("a", ToolMeta.Type.TString, true, "desc a"),
							ToolMeta.Prop("b", ToolMeta.Type.TInt, true, "desc b"),
						)
					)
				)
			),
			AllRequired.serializer() as KSerializer<ToolArgs>,
		)
		val result = assembleWithTool(tool)
		val required = result!![0].parameters.jsonObject["required"]?.jsonArray
		val requiredNames = required!!.map { it.jsonPrimitive.content }.toSet()
		assertTrue(requiredNames.contains("a"))
		assertTrue(requiredNames.contains("b"))
		assertTrue(requiredNames.contains("reason"))
		assertEquals(3, requiredNames.size)
	}
	
	// endregion
}
