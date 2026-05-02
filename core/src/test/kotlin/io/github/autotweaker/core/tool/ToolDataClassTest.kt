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

package io.github.autotweaker.core.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.session.workspace.Workspace
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path
import kotlin.test.*

class ToolDataClassTest {
    
    // region Meta
    
    @Test
    fun `Meta construction and copy`() {
        val meta = Tool.Meta(
            name = "test",
            description = "desc",
            functions = emptyList(),
        )
        assertEquals("test", meta.name)
        assertEquals("desc", meta.description)
        assertTrue(meta.functions.isEmpty())
        
        val copied = meta.copy(name = "renamed")
        assertEquals("renamed", copied.name)
        assertEquals("desc", copied.description)
    }
    
    // endregion
    
    // region Function
    
    @Test
    fun `Function construction and copy`() {
        val func = Tool.Function(
            name = "run",
            description = "runs something",
            parameters = emptyMap(),
        )
        assertEquals("run", func.name)
        assertEquals("runs something", func.description)
        assertTrue(func.parameters.isEmpty())
        
        val copied = func.copy(name = "execute")
        assertEquals("execute", copied.name)
    }
    
    // endregion
    
    // region Property
    
    @Test
    fun `Property construction and copy`() {
        val prop = Tool.Function.Property(
            description = "a parameter",
            required = true,
            valueType = Tool.Function.Property.ValueType.StringValue(),
        )
        assertEquals("a parameter", prop.description)
        assertTrue(prop.required)
        assertTrue(prop.valueType is Tool.Function.Property.ValueType.StringValue)
        
        val copied = prop.copy(required = false)
        assertFalse(copied.required)
    }
    
    // endregion
    
    // region ValueType - StringValue
    
    @Test
    fun `StringValue default construction`() {
        val vt = Tool.Function.Property.ValueType.StringValue()
        assertNull(vt.enum)
    }
    
    @Test
    fun `StringValue with enum`() {
        val vt = Tool.Function.Property.ValueType.StringValue(enum = listOf("a", "b"))
        assertEquals(listOf("a", "b"), vt.enum)
    }
    
    @Test
    fun `StringValue copy`() {
        val vt = Tool.Function.Property.ValueType.StringValue(enum = listOf("x"))
        val copied = vt.copy(enum = listOf("y"))
        assertEquals(listOf("y"), copied.enum)
    }
    
    // endregion
    
    // region ValueType - NumberValue
    
    @Test
    fun `NumberValue default construction`() {
        val vt = Tool.Function.Property.ValueType.NumberValue()
        assertNull(vt.enum)
    }
    
    @Test
    fun `NumberValue with enum`() {
        val vt = Tool.Function.Property.ValueType.NumberValue(enum = listOf(1.0, 2.5))
        assertEquals(listOf(1.0, 2.5), vt.enum)
    }
    
    @Test
    fun `NumberValue copy`() {
        val vt = Tool.Function.Property.ValueType.NumberValue(enum = listOf(3.14))
        val copied = vt.copy(enum = listOf(2.71))
        assertEquals(listOf(2.71), copied.enum)
    }
    
    // endregion
    
    // region ValueType - IntegerValue
    
    @Test
    fun `IntegerValue default construction`() {
        val vt = Tool.Function.Property.ValueType.IntegerValue()
        assertNull(vt.enum)
    }
    
    @Test
    fun `IntegerValue with enum`() {
        val vt = Tool.Function.Property.ValueType.IntegerValue(enum = listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), vt.enum)
    }
    
    @Test
    fun `IntegerValue copy`() {
        val vt = Tool.Function.Property.ValueType.IntegerValue(enum = listOf(10))
        val copied = vt.copy(enum = listOf(20))
        assertEquals(listOf(20), copied.enum)
    }
    
    // endregion
    
    // region ValueType - BooleanValue
    
    @Test
    fun `BooleanValue is singleton`() {
        val vt = Tool.Function.Property.ValueType.BooleanValue
        assertSame(vt, Tool.Function.Property.ValueType.BooleanValue)
    }
    
    // endregion
    
    // region ValueType - ArrayValue
    
    @Test
    fun `ArrayValue construction and copy`() {
        val itemType = Tool.Function.Property.ValueType.StringValue()
        val vt = Tool.Function.Property.ValueType.ArrayValue(items = itemType)
        assertSame(itemType, vt.items)
        
        val newItemType = Tool.Function.Property.ValueType.IntegerValue()
        val copied = vt.copy(items = newItemType)
        assertSame(newItemType, copied.items)
    }
    
    // endregion
    
    // region ValueType - ObjectValue
    
    @Test
    fun `ObjectValue default construction`() {
        val vt = Tool.Function.Property.ValueType.ObjectValue(properties = emptyMap())
        assertTrue(vt.properties.isEmpty())
    }
    
    @Test
    fun `ObjectValue with properties`() {
        val props = mapOf(
            "name" to Tool.Function.Property.ValueType.StringValue(),
            "age" to Tool.Function.Property.ValueType.IntegerValue(),
        )
        val vt = Tool.Function.Property.ValueType.ObjectValue(properties = props)
        assertEquals(2, vt.properties.size)
        assertTrue(vt.properties["name"] is Tool.Function.Property.ValueType.StringValue)
        assertTrue(vt.properties["age"] is Tool.Function.Property.ValueType.IntegerValue)
    }
    
    @Test
    fun `ObjectValue copy`() {
        val vt = Tool.Function.Property.ValueType.ObjectValue(
            properties = mapOf("a" to Tool.Function.Property.ValueType.StringValue())
        )
        val copied = vt.copy(properties = emptyMap())
        assertTrue(copied.properties.isEmpty())
    }
    
    // endregion
    
    // region RuntimeOutput
    
    @Test
    fun `RuntimeOutput construction and copy`() {
        val output = Tool.RuntimeOutput(content = "hello")
        assertEquals("hello", output.content)
        
        val copied = output.copy(content = "world")
        assertEquals("world", copied.content)
    }
    
    // endregion
    
    // region ToolOutput
    
    @Test
    fun `ToolOutput successful construction`() {
        val output = Tool.ToolOutput(result = "done", success = true)
        assertEquals("done", output.result)
        assertTrue(output.success)
    }
    
    @Test
    fun `ToolOutput failed construction`() {
        val output = Tool.ToolOutput(result = "error", success = false)
        assertEquals("error", output.result)
        assertFalse(output.success)
    }
    
    @Test
    fun `ToolOutput copy`() {
        val output = Tool.ToolOutput(result = "ok", success = true)
        val copied = output.copy(success = false)
        assertEquals("ok", copied.result)
        assertFalse(copied.success)
    }
    
    // endregion
    
    // region ToolInput
    
    @Test
    fun `ToolInput construction with mandatory fields`() {
        val input = Tool.ToolInput(
            functionName = "run",
            arguments = buildJsonObject { put("cmd", JsonPrimitive("echo hello")) },
            provider = SimpleContainer(),
            settings = emptyList(),
            workspace = Workspace("test", false, Path.of("/tmp/test")),
        )
        assertEquals("run", input.functionName)
        assertNull(input.outputChannel)
    }
    
    @Test
    fun `ToolInput construction with outputChannel`() {
        val channel = Channel<Tool.RuntimeOutput>(Channel.UNLIMITED)
        val input = Tool.ToolInput(
            functionName = "run",
            arguments = buildJsonObject { },
            provider = SimpleContainer(),
            settings = emptyList(),
            workspace = Workspace("test", false, Path.of("/tmp/test")),
            outputChannel = channel,
        )
        assertSame(channel, input.outputChannel)
    }
    
    @Test
    fun `ToolInput copy`() {
        val input = Tool.ToolInput(
            functionName = "run",
            arguments = buildJsonObject { },
            provider = SimpleContainer(),
            settings = emptyList(),
            workspace = Workspace("test", false, Path.of("/tmp/test")),
        )
        val copied = input.copy(functionName = "execute")
        assertEquals("execute", copied.functionName)
    }
    
    // endregion
    
    // region DependencyProvider inline extension
    
    @Test
    fun `DependencyProvider inline extension with SimpleContainer`() {
        val container = SimpleContainer()
        val svc = object : Tool {
            override fun resolveMeta(settings: List<SettingItem>): Tool.Meta =
                Tool.Meta("test", "desc", emptyList())
            
            override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput =
                Tool.ToolOutput("ok", true)
        }
        container.register(Tool::class, svc)
        
        val resolved: Tool = container.get()
        assertSame(svc, resolved)
    }
    
    // endregion
}
