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
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator.ValidationResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import io.github.autotweaker.api.tool.ToolArgs

class ToolCallValidatorSealedTest {
	
	private val defaultSettings: SettingService = mockk<SettingService>().also { svc ->
		every { svc.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	
	// region test data
	
	@Serializable
	private sealed class InnerChoice : ToolArgs {
		@Serializable
		data class A(val x: Int) : InnerChoice()
		
		@Serializable
		data class B(val y: String) : InnerChoice()
	}
	
	@Serializable
	private sealed class NestedSealedArgs : ToolArgs {
		@Serializable
		data class Process(
			val name: String,
			val inner: InnerChoice,
		) : NestedSealedArgs()
		
		@Serializable
		data class Simple(val value: String) : NestedSealedArgs()
	}
	
	@Serializable
	private sealed class ListSealedArgs : ToolArgs {
		@Serializable
		data class Run(val items: List<InnerChoice>) : ListSealedArgs()
		
		@Serializable
		data class Stop(val reason: String) : ListSealedArgs()
	}
	
	@Serializable
	private sealed class MapSealedArgs : ToolArgs {
		@Serializable
		data class Configure(val config: Map<String, InnerChoice>) : MapSealedArgs()
		
		@Serializable
		data class Reset(val reason: String) : MapSealedArgs()
	}
	
	@Serializable
	private data class Wrapper(val inner: InnerChoice) : ToolArgs
	
	@Serializable
	private sealed class DeepNestedArgs : ToolArgs {
		@Serializable
		data class Process(val wrapper: Wrapper) : DeepNestedArgs()
		
		@Serializable
		data class Skip(val reason: String) : DeepNestedArgs()
	}
	
	// endregion
	
	// region helpers
	
	@Suppress("UNCHECKED_CAST")
	private fun mockNestedSealedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<NestedSealedArgs>>()
		every { tool.name } returns "task"
		every { tool.description } returns "Task operations"
		every { tool.argsSerializer } returns NestedSealedArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockListSealedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<ListSealedArgs>>()
		every { tool.name } returns "batch"
		every { tool.description } returns "Batch operations"
		every { tool.argsSerializer } returns ListSealedArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockMapSealedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<MapSealedArgs>>()
		every { tool.name } returns "cfg"
		every { tool.description } returns "Config operations"
		every { tool.argsSerializer } returns MapSealedArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockDeepNestedTool(): Tool<ToolArgs> {
		val tool = mockk<Tool<DeepNestedArgs>>()
		every { tool.name } returns "deep"
		every { tool.description } returns "Deep nested operations"
		every { tool.argsSerializer } returns DeepNestedArgs.serializer()
		return tool as Tool<ToolArgs>
	}
	
	// endregion
	
	// region nested sealed
	
	@Test
	fun `nested sealed type is resolved correctly`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"a","x":42},"reason":"test"}""", "", listOf(mockNestedSealedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as NestedSealedArgs.Process
		assertEquals("test", args.name)
		assertEquals(42, (args.inner as InnerChoice.A).x)
	}
	
	@Test
	fun `nested sealed type with variant b works`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"b","y":"hello"},"reason":"test"}""", "", listOf(mockNestedSealedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("hello", ((result.args as NestedSealedArgs.Process).inner as InnerChoice.B).y)
	}
	
	// endregion
	
	// region list of sealed
	
	@Test
	fun `list of sealed types is resolved correctly`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"batch-run",
			"""{"items":[{"type":"a","x":1},{"type":"b","y":"two"}],"reason":"test"}""", "", listOf(mockListSealedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as ListSealedArgs.Run
		assertEquals(2, args.items.size)
		assertIs<InnerChoice.A>(args.items[0])
		assertIs<InnerChoice.B>(args.items[1])
	}
	
	@Test
	fun `list with mixed sealed types resolves each independently`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"batch-run",
			"""{"items":[{"type":"a","x":10},{"type":"b","y":"hello"},{"type":"a","x":20}],"reason":"test"}""", "", listOf(mockListSealedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as ListSealedArgs.Run
		assertEquals(3, args.items.size)
		assertEquals(10, (args.items[0] as InnerChoice.A).x)
		assertEquals(20, (args.items[2] as InnerChoice.A).x)
	}
	
	// endregion
	
	// region map of sealed
	
	@Test
	fun `map of sealed types is resolved correctly`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"cfg-configure",
			"""{"config":{"rule1":{"type":"a","x":1},"rule2":{"type":"b","y":"two"}},"reason":"test"}""", "", listOf(mockMapSealedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as MapSealedArgs.Configure
		assertEquals(2, args.config.size)
		assertIs<InnerChoice.A>(args.config["rule1"])
		assertIs<InnerChoice.B>(args.config["rule2"])
	}
	
	// endregion
	
	// region deep nested sealed
	
	@Test
	fun `deep nested sealed type is resolved correctly`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"deep-process",
			"""{"wrapper":{"inner":{"type":"a","x":42}},"reason":"test"}""", "", listOf(mockDeepNestedTool()))
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals(42, ((result.args as DeepNestedArgs.Process).wrapper.inner as InnerChoice.A).x)
	}
	
	// endregion
	
	// region edge cases
	
	@Test
	fun `nested sealed without type field fails deserialization`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"x":42},"reason":"test"}""", "", listOf(mockNestedSealedTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	@Test
	fun `nested sealed with unknown type value fails deserialization`() {
		val validator = ToolCallValidator(defaultSettings)
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"unknown","x":42},"reason":"test"}""", "", listOf(mockNestedSealedTool()))
		assertIs<ValidationResult.Failure>(result)
	}
	
	// endregion
}
