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

class ToolCallValidatorSealedTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region test data
	
	@Serializable
	sealed class InnerChoice : ToolArgs {
		@Serializable
		@SerialName("a")
		data class A(val x: Int) : InnerChoice()
		
		@Serializable
		@SerialName("b")
		data class B(val y: String) : InnerChoice()
	}
	
	@Serializable
	private sealed class NestedSealedArgs : ToolArgs {
		@Serializable
		@SerialName("process")
		data class Process(
			val name: String,
			val inner: InnerChoice,
		) : NestedSealedArgs()
		
		@Serializable
		@SerialName("simple")
		data class Simple(val value: String) : NestedSealedArgs()
	}
	
	@Serializable
	private sealed class ListSealedArgs : ToolArgs {
		@Serializable
		@SerialName("run")
		data class Run(val items: List<InnerChoice>) : ListSealedArgs()
		
		@Serializable
		@SerialName("stop")
		data class Stop(val reason: String) : ListSealedArgs()
	}
	
	@Serializable
	private sealed class MapSealedArgs : ToolArgs {
		@Serializable
		@SerialName("configure")
		data class Configure(val config: Map<String, InnerChoice>) : MapSealedArgs()
		
		@Serializable
		@SerialName("reset")
		data class Reset(val reason: String) : MapSealedArgs()
	}
	
	@Serializable
	private data class Wrapper(val inner: InnerChoice) : ToolArgs
	
	@Serializable
	private sealed class DeepNestedArgs : ToolArgs {
		@Serializable
		@SerialName("process")
		data class Process(val wrapper: Wrapper) : DeepNestedArgs()
		
		@Serializable
		@SerialName("skip")
		data class Skip(val reason: String) : DeepNestedArgs()
	}
	
	// endregion
	
	// region helpers
	
	@Suppress("UNCHECKED_CAST")
	private fun mockNestedSealedTool(): Tool<*> {
		val tool = mockk<Tool<NestedSealedArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"task", "Task operations", listOf(
					ToolMeta.Function("process", "Process task", emptyList()),
					ToolMeta.Function("simple", "Simple task", emptyList()),
				)
			),
			NestedSealedArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockListSealedTool(): Tool<*> {
		val tool = mockk<Tool<ListSealedArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"batch", "Batch operations", listOf(
					ToolMeta.Function("run", "Run batch", emptyList()),
					ToolMeta.Function("stop", "Stop batch", emptyList()),
				)
			),
			ListSealedArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockMapSealedTool(): Tool<*> {
		val tool = mockk<Tool<MapSealedArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"cfg", "Config operations", listOf(
					ToolMeta.Function("configure", "Configure settings", emptyList()),
					ToolMeta.Function("reset", "Reset settings", emptyList()),
				)
			),
			MapSealedArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun mockDeepNestedTool(): Tool<*> {
		val tool = mockk<Tool<DeepNestedArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				"deep", "Deep nested operations", listOf(
					ToolMeta.Function("process", "Process deeply", emptyList()),
					ToolMeta.Function("skip", "Skip processing", emptyList()),
				)
			),
			DeepNestedArgs.serializer()
		)
		return tool as Tool<*>
	}
	
	@Suppress("UNCHECKED_CAST")
	private suspend fun toolMetaCache(vararg tools: Tool<*>): MetaCache =
		Tools.cacheMeta(tools.associate { it.meta().first.name to it as Tool<ToolArgs> })
	
	// endregion
	
	// region nested sealed
	
	@Test
	fun `nested sealed type is resolved correctly`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"a","x":42},"reason":"tests"}""", "",
			toolMetaCache(mockNestedSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as NestedSealedArgs.Process
		assertEquals("test", args.name)
		assertEquals(42, (args.inner as InnerChoice.A).x)
	}
	
	@Test
	fun `nested sealed type with variant b works`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"b","y":"hello"},"reason":"tests"}""", "",
			toolMetaCache(mockNestedSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals("hello", ((result.args as NestedSealedArgs.Process).inner as InnerChoice.B).y)
	}
	
	// endregion
	
	// region list of sealed
	
	@Suppress("EXPOSED_FROM_PRIVATE_IN_CLASS")
	@Test
	fun `list of sealed types is resolved correctly`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"batch-run",
			"""{"items":[{"type":"a","x":1},{"type":"b","y":"two"}],"reason":"tests"}""",
			"",
			toolMetaCache(mockListSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as ListSealedArgs.Run
		assertEquals(2, args.items.size)
		assertIs<InnerChoice.A>(args.items[0])
		assertIs<InnerChoice.B>(args.items[1])
	}.discard()
	
	@Test
	fun `list with mixed sealed types resolves each independently`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"batch-run",
			"""{"items":[{"type":"a","x":10},{"type":"b","y":"hello"},{"type":"a","x":20}],"reason":"tests"}""",
			"",
			toolMetaCache(mockListSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as ListSealedArgs.Run
		assertEquals(3, args.items.size)
		assertEquals(10, (args.items[0] as InnerChoice.A).x)
		assertEquals(20, (args.items[2] as InnerChoice.A).x)
	}
	
	// endregion
	
	// region map of sealed
	
	@Suppress("EXPOSED_FROM_PRIVATE_IN_CLASS")
	@Test
	fun `map of sealed types is resolved correctly`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"cfg-configure",
			"""{"config":{"rule1":{"type":"a","x":1},"rule2":{"type":"b","y":"two"}},"reason":"tests"}""",
			"",
			toolMetaCache(mockMapSealedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		val args = result.args as MapSealedArgs.Configure
		assertEquals(2, args.config.size)
		assertIs<InnerChoice.A>(args.config["rule1"])
		assertIs<InnerChoice.B>(args.config["rule2"])
	}.discard()
	
	// endregion
	
	// region deep nested sealed
	
	@Test
	fun `deep nested sealed type is resolved correctly`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"deep-process",
			"""{"wrapper":{"inner":{"type":"a","x":42}},"reason":"tests"}""", "",
			toolMetaCache(mockDeepNestedTool()),
		)
		assertIs<ValidationResult.Success<*>>(result)
		assertEquals(42, ((result.args as DeepNestedArgs.Process).wrapper.inner as InnerChoice.A).x)
	}
	
	// endregion
	
	// region edge cases
	
	@Test
	fun `nested sealed without type field fails deserialization`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"x":42},"reason":"tests"}""", "",
			toolMetaCache(mockNestedSealedTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	@Test
	fun `nested sealed with unknown type value fails deserialization`() = runBlocking {
		val validator = ToolCallParser()
		val result = validator.validate(
			"task-process",
			"""{"name":"test","inner":{"type":"unknown","x":42},"reason":"tests"}""", "",
			toolMetaCache(mockNestedSealedTool()),
		)
		assertIs<ValidationResult.Failure>(result)
	}.discard()
	
	// endregion
}
