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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class ToolMetaValueTypeTest {
	
	// region primitives
	
	@Test
	fun `string descriptor maps to StringValue`() = runBlocking {
		@Serializable
		data class Args(val name: String)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::name to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["name"]!!.valueType is ToolMeta.ValueType.StringValue)
	}
	
	@Test
	fun `int descriptor maps to IntegerValue`() = runBlocking {
		@Serializable
		data class Args(val count: Int)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::count to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["count"]!!.valueType is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `long descriptor maps to IntegerValue`() = runBlocking {
		@Serializable
		data class Args(val big: Long)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::big to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["big"]!!.valueType is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `float descriptor maps to NumberValue`() = runBlocking {
		@Serializable
		data class Args(val ratio: Float)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::ratio to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["ratio"]!!.valueType is ToolMeta.ValueType.NumberValue)
	}
	
	@Test
	fun `double descriptor maps to NumberValue`() = runBlocking {
		@Serializable
		data class Args(val price: Double)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::price to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["price"]!!.valueType is ToolMeta.ValueType.NumberValue)
	}
	
	@Test
	fun `boolean descriptor maps to BooleanValue`() = runBlocking {
		@Serializable
		data class Args(val flag: Boolean)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::flag to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["flag"]!!.valueType is ToolMeta.ValueType.BooleanValue)
	}
	
	// endregion
	
	// region collections
	
	@Test
	fun `list of strings maps to ArrayValue with StringValue items`() = runBlocking {
		@Serializable
		data class Args(val items: List<String>)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::items to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["items"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.ArrayValue)
		assertTrue(vt.items is ToolMeta.ValueType.StringValue)
	}
	
	@Test
	fun `list of ints maps to ArrayValue with IntegerValue items`() = runBlocking {
		@Serializable
		data class Args(val numbers: List<Int>)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::numbers to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["numbers"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.ArrayValue)
		assertTrue(vt.items is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `map of string to int maps to MapValue`() = runBlocking {
		@Serializable
		data class Args(val config: Map<String, Int>)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::config to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["config"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.MapValue)
		assertTrue(vt.key is ToolMeta.ValueType.StringValue)
		assertTrue(vt.value is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `map of string to string maps to MapValue`() = runBlocking {
		@Serializable
		data class Args(val env: Map<String, String>)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::env to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["env"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.MapValue)
		assertTrue(vt.key is ToolMeta.ValueType.StringValue)
		assertTrue(vt.value is ToolMeta.ValueType.StringValue)
	}
	
	// endregion
	
	// region JsonElement types
	
	@Test
	fun `JsonElement maps to AnyValue`() = runBlocking {
		@Serializable
		data class Args(val data: JsonElement)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::data to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["data"]!!.valueType is ToolMeta.ValueType.AnyValue)
	}
	
	@Test
	fun `JsonObject maps to AnyValue`() = runBlocking {
		@Serializable
		data class Args(val obj: JsonObject)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::obj to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["obj"]!!.valueType is ToolMeta.ValueType.AnyValue)
	}
	
	@Test
	fun `JsonArray maps to ArrayValue with AnyValue items`() = runBlocking {
		@Serializable
		data class Args(val arr: JsonArray)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::arr to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["arr"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.ArrayValue)
		assertTrue(vt.items is ToolMeta.ValueType.AnyValue)
	}
	
	@Test
	fun `JsonPrimitive maps to AnyValue`() = runBlocking {
		@Serializable
		data class Args(val prim: JsonPrimitive)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::prim to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertTrue(meta.functions[0].parameters["prim"]!!.valueType is ToolMeta.ValueType.AnyValue)
	}
	
	// endregion
	
	// region snake_case conversion
	
	@Test
	fun `camelCase property names converted to snake_case`() = runBlocking {
		@Serializable
		data class Args(val myFieldName: String, val anotherProp: Int)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(
			Args::myFieldName to "desc1",
			Args::anotherProp to "desc2",
		)
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val params = meta.functions[0].parameters
		assertNotNull(params["my_field_name"])
		assertNotNull(params["another_prop"])
		assertNull(params["myFieldName"])
		assertNull(params["anotherProp"])
	}
	
	@Test
	fun `already snake_case names stay unchanged`() = runBlocking {
		@Serializable
		data class Args(val already_snake: String)
		
		val tool = mockk<Tool<Args>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns Args.serializer()
		coEvery { tool.describe() } returns mapOf(Args::already_snake to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		assertNotNull(meta.functions[0].parameters["already_snake"])
	}
	
	// endregion
	
	// region enum type
	
	@Serializable
	private enum class Color { RED, GREEN, BLUE }
	
	@Serializable
	private data class EnumArgs(val color: Color)
	
	@Test
	fun `enum descriptor maps to StringValue with enum list`() = runBlocking {
		val tool = mockk<Tool<EnumArgs>>()
		every { tool.name } returns "t"
		every { tool.description } returns "d"
		every { tool.argsSerializer } returns EnumArgs.serializer()
		coEvery { tool.describe() } returns mapOf(EnumArgs::color to "desc")
		coEvery { tool.describeFunctions() } returns emptyMap()
		
		val meta = ToolMeta.build(tool)
		val vt = meta.functions[0].parameters["color"]!!.valueType
		assertTrue(vt is ToolMeta.ValueType.StringValue)
		val enum = (vt as ToolMeta.ValueType.StringValue).enum
		assertNotNull(enum)
		assertTrue(enum.contains("RED"))
		assertTrue(enum.contains("GREEN"))
		assertTrue(enum.contains("BLUE"))
	}
	
	// endregion
}
