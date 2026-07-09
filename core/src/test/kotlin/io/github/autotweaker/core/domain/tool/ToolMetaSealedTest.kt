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
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.tool.args.edit.EditArgs
import io.github.autotweaker.core.TestServices
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class ToolMetaSealedTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region guard test data (top-level for @SerialName / @JsonClassDiscriminator)
	
	@Serializable
	private sealed class SealedWithCustomName : ToolArgs {
		@Serializable
		@SerialName("custom")
		data class Sub(val x: String) : SealedWithCustomName()
	}
	
	@Serializable
	@JsonClassDiscriminator("kind")
	private sealed class CustomDiscriminator : ToolArgs {
		@Serializable
		data class Sub(val x: String) : CustomDiscriminator()
	}
	
	// endregion
	
	// region sealed class edge cases
	
	@Serializable
	private sealed class TwoOptions : ToolArgs {
		@Serializable
		data class OptA(val value: String) : TwoOptions()
		
		@Serializable
		data class OptB(val count: Int) : TwoOptions()
	}
	
	@Serializable
	private sealed class Single : ToolArgs {
		@Serializable
		data class Only(val x: Int) : Single()
	}
	
	@Serializable
	private sealed class MixedArgs : ToolArgs {
		@Serializable
		data class ReadFile(val filePath: String, val encoding: String = "utf-8") : MixedArgs()
		
		@Serializable
		data object ListFiles : MixedArgs()
		
		@Serializable
		data object GetStatus : MixedArgs()
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockMixedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<MixedArgs>>()
		every { tool.name } returns "fs"
		every { tool.description } returns "File system operations"
		every { tool.argsSerializer } returns MixedArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			MixedArgs.ReadFile::filePath to "Path to file",
			MixedArgs.ReadFile::encoding to "File encoding",
		)
		coEvery { tool.describeFunctions() } returns mapOf(
			MixedArgs.ReadFile::class to "Read file content",
			MixedArgs.ListFiles::class to "List all files in directory",
			MixedArgs.GetStatus::class to "Get system status",
		)
		return tool as Tool<ToolArgs>
	}
	
	@Test
	fun `sealed class with two subclasses`() = runBlocking {
		val tool = mockk<Tool<TwoOptions>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns TwoOptions.serializer()
		coEvery { tool.describe() } returns mapOf(
			TwoOptions.OptA::value to "A value",
			TwoOptions.OptB::count to "B count",
		)
		coEvery { tool.describeFunctions() } returns mapOf(
			TwoOptions.OptA::class to "Option A",
			TwoOptions.OptB::class to "Option B",
		)
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(tool as Tool<ToolArgs>)
		assertEquals(2, meta.functions.size)
		assertTrue(meta.functions.any { it.name == "opt_a" })
		assertTrue(meta.functions.any { it.name == "opt_b" })
	}
	
	@Test
	fun `sealed class single subclass`() = runBlocking {
		val tool = mockk<Tool<Single>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Single.serializer()
		coEvery { tool.describe() } returns mapOf(Single.Only::x to "x val")
		coEvery { tool.describeFunctions() } returns mapOf(Single.Only::class to "Only option")
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(tool as Tool<ToolArgs>)
		assertEquals(1, meta.functions.size)
		assertEquals("only", meta.functions[0].name)
	}
	
	@Test
	fun `sealed class with object subclass builds all functions`() = runBlocking {
		val meta = ToolMeta.build(mockMixedTool())
		assertEquals(3, meta.functions.size)
		assertTrue(meta.functions.any { it.name == "read_file" })
		assertTrue(meta.functions.any { it.name == "list_files" })
		assertTrue(meta.functions.any { it.name == "get_status" })
	}
	
	@Test
	fun `object function has no parameters`() = runBlocking {
		val meta = ToolMeta.build(mockMixedTool())
		val listFiles = meta.functions.first { it.name == "list_files" }
		assertTrue(listFiles.parameters.isEmpty())
		val getStatus = meta.functions.first { it.name == "get_status" }
		assertTrue(getStatus.parameters.isEmpty())
	}
	
	@Test
	fun `object function uses describeFunctions description`() = runBlocking {
		val meta = ToolMeta.build(mockMixedTool())
		assertEquals("List all files in directory", meta.functions.first { it.name == "list_files" }.description)
		assertEquals("Get system status", meta.functions.first { it.name == "get_status" }.description)
	}
	
	@Test
	fun `data class function mixed with object functions works`() = runBlocking {
		val meta = ToolMeta.build(mockMixedTool())
		val readFile = meta.functions.first { it.name == "read_file" }
		assertEquals("Read file content", readFile.description)
		assertEquals(2, readFile.parameters.size)
		assertTrue(readFile.parameters["file_path"]!!.required)
		assertFalse(readFile.parameters["encoding"]!!.required)
	}
	
	// endregion
	
	// region OneOfValue
	
	@Serializable
	private sealed class InnerChoice : ToolArgs {
		@Serializable
		data class A(val x: Int) : InnerChoice()
		
		@Serializable
		data class B(val y: String) : InnerChoice()
	}
	
	@Serializable
	private data class NestedSealedArgs(
		val name: String,
		val inner: InnerChoice,
	) : ToolArgs
	
	@Test
	fun `nested sealed field produces OneOfValue`() = runBlocking {
		val tool = mockk<Tool<NestedSealedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns NestedSealedArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			NestedSealedArgs::name to "A name",
			NestedSealedArgs::inner to "Inner choice",
		)
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(tool as Tool<ToolArgs>)
		val innerType = meta.functions[0].parameters["inner"]!!.valueType
		assertTrue(innerType is ToolMeta.ValueType.OneOfValue)
		val oneOf = innerType as ToolMeta.ValueType.OneOfValue
		assertEquals(2, oneOf.variants.size)
		assertTrue(oneOf.variants.containsKey("a"))
		assertTrue(oneOf.variants.containsKey("b"))
		val variantA = oneOf.variants["a"]!! as ToolMeta.ValueType.ObjectValue
		assertTrue(variantA.properties["x"] is ToolMeta.ValueType.IntegerValue)
		val variantB = oneOf.variants["b"]!! as ToolMeta.ValueType.ObjectValue
		assertTrue(variantB.properties["y"] is ToolMeta.ValueType.StringValue)
	}
	
	@Serializable
	private data class ListSealedArgs(
		val items: List<InnerChoice>,
	) : ToolArgs
	
	@Test
	fun `list of sealed produces ArrayValue with OneOfValue items`() = runBlocking {
		val tool = mockk<Tool<ListSealedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns ListSealedArgs.serializer()
		coEvery { tool.describe() } returns mapOf(ListSealedArgs::items to "Items")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(tool as Tool<ToolArgs>)
		val itemsType = meta.functions[0].parameters["items"]!!.valueType
		assertTrue(itemsType is ToolMeta.ValueType.ArrayValue)
		assertTrue((itemsType as ToolMeta.ValueType.ArrayValue).items is ToolMeta.ValueType.OneOfValue)
	}
	
	// endregion
	
	// region buildTypeMapping
	
	@Serializable
	private data class SimpleArgs(val command: String) : ToolArgs
	
	@Test
	fun `buildTypeMapping returns empty for non-sealed tool`() = runBlocking {
		val tool = mockk<Tool<SimpleArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns SimpleArgs.serializer()
		
		val mapping = ToolMeta.buildTypeMapping(tool as Tool<ToolArgs>)
		assertTrue(mapping.isEmpty())
	}
	
	@Test
	fun `buildTypeMapping returns nested sealed paths`() = runBlocking {
		val tool = mockk<Tool<NestedSealedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns NestedSealedArgs.serializer()
		
		val mapping = ToolMeta.buildTypeMapping(tool as Tool<ToolArgs>)
		assertEquals(1, mapping.size)
		val sp = mapping[0]
		assertEquals(listOf("inner"), sp.segments)
		assertTrue(sp.typeMap.containsKey("a"))
		assertTrue(sp.typeMap["a"]!!.endsWith("InnerChoice.A"))
	}
	
	@Test
	fun `buildTypeMapping handles list of sealed`() = runBlocking {
		val tool = mockk<Tool<ListSealedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns ListSealedArgs.serializer()
		
		val mapping = ToolMeta.buildTypeMapping(tool as Tool<ToolArgs>)
		assertEquals(1, mapping.size)
		assertEquals(listOf("items"), mapping[0].segments)
	}
	
	@Serializable
	private data class MapSealedArgs(
		val config: Map<String, InnerChoice>,
	) : ToolArgs
	
	@Test
	fun `buildTypeMapping handles map of sealed`() = runBlocking {
		val tool = mockk<Tool<MapSealedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns MapSealedArgs.serializer()
		
		val mapping = ToolMeta.buildTypeMapping(tool as Tool<ToolArgs>)
		assertEquals(1, mapping.size)
		assertEquals(listOf("config"), mapping[0].segments)
	}
	
	@Serializable
	private data class Wrapper(val inner: InnerChoice) : ToolArgs
	
	@Serializable
	private data class DeepNestedArgs(
		val wrapper: Wrapper,
	) : ToolArgs
	
	@Test
	fun `buildTypeMapping handles deep nested sealed`() = runBlocking {
		val tool = mockk<Tool<DeepNestedArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns DeepNestedArgs.serializer()
		
		val mapping = ToolMeta.buildTypeMapping(tool as Tool<ToolArgs>)
		assertEquals(1, mapping.size)
		assertEquals(listOf("wrapper", "inner"), mapping[0].segments)
	}
	
	// endregion
	
	// region @SerialName guard
	
	@Test
	fun `sealed subclass with SerialName throws`() = runBlocking {
		val tool = mockk<Tool<SealedWithCustomName>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns SealedWithCustomName.serializer()
		coEvery { tool.describe() } returns mapOf(SealedWithCustomName.Sub::x to "desc")
		coEvery { tool.describeFunctions() } returns mapOf(SealedWithCustomName.Sub::class to "Sub")
		
		assertFailsWith<IllegalArgumentException> {
			ToolMeta.build(tool as Tool<ToolArgs>)
		}
	}
	
	// endregion
	
	// region @JsonClassDiscriminator guard
	
	@Test
	fun `sealed base with custom discriminator throws`() = runBlocking {
		val tool = mockk<Tool<CustomDiscriminator>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns CustomDiscriminator.serializer()
		coEvery { tool.describe() } returns mapOf(CustomDiscriminator.Sub::x to "desc")
		coEvery { tool.describeFunctions() } returns mapOf(CustomDiscriminator.Sub::class to "Sub")
		
		assertFailsWith<IllegalArgumentException> {
			ToolMeta.build(tool as Tool<ToolArgs>)
		}
	}
	
	// endregion
	
	// region EditArgs — real-world sealed class with deeply nested sealed hierarchy
	
	@Suppress("UNCHECKED_CAST")
	private fun mockEditTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<EditArgs>>()
		every { tool.name } returns "edit"
		every { tool.description } returns "Edit files"
		every { tool.argsSerializer } returns EditArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			EditArgs.Run::files to "Files to edit",
			EditArgs.Run::actions to "Edit actions to perform",
			EditArgs.Run::dryRun to "Preview changes without applying",
			EditArgs.Apply::operationId to "Operation ID to apply",
			EditArgs.GetClip::clipId to "Clip ID to retrieve",
		)
		coEvery { tool.describeFunctions() } returns mapOf(
			EditArgs.Run::class to "Run edit actions on files",
			EditArgs.Apply::class to "Apply a previously reviewed operation",
			EditArgs.GetClip::class to "Get clipboard content by ID",
		)
		return tool as Tool<ToolArgs>
	}
	
	@Test
	fun `EditArgs builds functions with nested sealed classes in list`() = runBlocking {
		val meta = ToolMeta.build(mockEditTool())
		
		val runFunc = meta.functions.first { it.name == "run" }
		assertEquals(3, runFunc.parameters.size)
		assertTrue(runFunc.parameters["files"]!!.required)
		assertTrue(runFunc.parameters["actions"]!!.required)
		assertFalse(runFunc.parameters["dry_run"]!!.required)
		
		val actionsType = runFunc.parameters["actions"]!!.valueType
		assertTrue(actionsType is ToolMeta.ValueType.ArrayValue)
		val variants = ((actionsType as ToolMeta.ValueType.ArrayValue).items as ToolMeta.ValueType.OneOfValue).variants
		assertEquals(6, variants.size)
		assertTrue(variants.containsKey("insert"))
		assertTrue(variants.containsKey("replace"))
		assertTrue(variants.containsKey("delete"))
		assertTrue(variants.containsKey("copy"))
		assertTrue(variants.containsKey("cut"))
		assertTrue(variants.containsKey("paste"))
		
		val applyFunc = meta.functions.first { it.name == "apply" }
		assertEquals(1, applyFunc.parameters.size)
		assertTrue(applyFunc.parameters["operation_id"]!!.required)
		
		val getClipFunc = meta.functions.first { it.name == "get_clip" }
		assertEquals(1, getClipFunc.parameters.size)
		assertTrue(getClipFunc.parameters["clip_id"]!!.required)
	}
	
	// endregion
}
