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

package io.github.autotweaker.api.tool

import io.github.autotweaker.api.types.tool.ToolMeta
import kotlinx.coroutines.channels.Channel

/**
 * 实现此接口并打上 `@AutoService(Tool::class)` 来注册成为 agent 的一个工具。
 *
 * AutoTweaker 的一个插件内部，或插件直接可以互相访问，故工具可以拿到来自适配器以及其他插件的能力。
 *
 * AutoTweaker 会为每个 agent 实例构造不同的 [Tool] 实例，在这个 agent 的生命周期中，始终使用同一个 [Tool] 实例。
 *
 * [ToolArgs] 是工具的调用参数，AutoTweaker 使用 kotlin 的序列化器处理 LLM 的工具调用参数来代替手动 Json 解析，同时提供类型安全的参数读取。
 *
 * @see ToolArgs
 */
interface Tool<Args : ToolArgs<Args>> {
	val meta: ToolMeta
	
	/**
	 * 调用工具，已经经过用户或审批系统确认，不必考虑安全问题，但不保证 [Args] 的参数（如文件路径）一定可用。
	 *
	 * @param outputChannel 工具的实时输出，如命令的实时响应，这些信息不会传递给 LLM，只给用户看。
	 * @return 不同于 [outputChannel]，这些内容直接返回给 LLM。
	 */
	suspend fun execute(args: Args, outputChannel: Channel<RuntimeOutput>? = null): ToolOutput
	
	/**
	 * 工具的实时输出，如命令的实时响应，这些信息不会传递给 LLM，只给用户看。
	 */
	data class RuntimeOutput(
		/**
		 * 给用户看的实时信息，不会传递给 LLM。
		 *
		 * 如果涉及，建议不要累加输出。
		 */
		val content: String,
		/**
		 * 输出的类型，不同类型的输出会以不同形式呈现给用户。
		 */
		val type: OutputType
	) {
		enum class OutputType {
			/**
			 * 普通输出，累加给用户看。
			 */
			INFO,
			
			/**
			 * 错误输出，通常以红色给用户看。
			 */
			ERROR,
			
			/**
			 * 状态输出，新状态覆盖旧状态，用户持续看到最新的状态，不会看到旧的。
			 */
			STATUS
		}
	}
	
	/**
	 * 工具响应，[result] 给 LLM 看，[success] 给用户看。
	 */
	data class ToolOutput(
		/**
		 * 返回给 LLM 的内容，超出阈值部分会被截断，完整内容存入文件。
		 *
		 * 阈值由用户配置，默认 50 万字符，“字符”的语义是 [String.length]。
		 */
		val result: String,
		/**
		 * 工具执行是否成功。给用户看。
		 */
		val success: Boolean,
	)
}
