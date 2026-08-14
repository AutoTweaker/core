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
import io.github.autotweaker.api.types.tool.ToolPresentation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import kotlin.time.Duration

/**
 * 实现此接口并打上 `@AutoService(Tool::class)` 来注册成为 agent 的一个工具。
 *
 * AutoTweaker 的一个插件内部，或插件直接可以互相访问，故工具可以拿到来自适配器以及其他插件的能力。
 *
 * AutoTweaker 会为每个 agent 实例构造不同的 [Tool] 实例，在这个 agent 的生命周期中，始终使用同一个 [Tool] 实例。
 *
 * [ToolArgs] 是工具的调用参数，应该由插件生成，AutoTweaker 使用 kotlin 的序列化器处理 LLM 的工具调用参数来代替手动 Json 解析，同时提供类型安全的参数读取。
 *
 * @see ToolArgs
 */
interface Tool<Args : ToolArgs> {
	/**
	 * 应该委托给插件生成的 builder 来构造 `Pair<ToolMeta, KSerializer<Args>>`。
	 *
	 * 此方法的返回值会被 AutoTweaker 缓存，并仅在新 Agent 创建或请求 LLM 前刷新，避免大型 I/O。
	 */
	suspend fun meta(): Pair<ToolMeta, KSerializer<Args>>
	
	/**
	 * 解析 LLM 的调用请求，在不执行任何实际操作的情况下对请求进行预处理，并在有必要时提前返回错误消息而不是等到用户批准之后。
	 *
	 * 在 LLM 生成工具调用请求，[ToolArgs] 反序列化成功后调用，此时未经过程序或用户审批，避免直接执行请求的操作。
	 *
	 * 可以在此时对请求进行业务校验，例如检查 String 是不是一个有效的 Path，startLine 有没有大于 endLine，并提前驳回工具调用。
	 *
	 * 避免进行影响外部环境操作，例如删除或修改文件，或其他影响系统或程序状态的动作，此时拿到的 [Args] 不会经过来自程序或用户的任何审查。
	 *
	 * @return 解析后的结果，如果为 [ResolveResult.Ready]，将会由程序或用户进行下一步审批。
	 */
	suspend fun resolve(args: Args, cwd: Path): ResolveResult
	
	/**
	 * 调用工具，已经经过用户或审批系统确认，不必考虑安全问题。
	 *
	 * @param request 来自 [resolve] 的返回值。
	 * @param outputChannel 工具的实时输出，如命令的实时响应，这些信息不会传递给 LLM，只给用户看。
	 * @return 不同于 [outputChannel]，这些内容直接返回给 LLM。
	 */
	suspend fun execute(request: JsonElement, cwd: Path, outputChannel: SendChannel<RuntimeOutput>): ToolOutput
	
	/**
	 * 预处理的结果，决定直接生成工具响应还是等待用户审批调用。
	 */
	sealed interface ResolveResult {
		/**
		 * 参数解析成功，接下来会由程序或用户进行审批，审批通过后会使用 [result] 作为 [execute] 的 `request`。
		 *
		 * @param result 建议反序列化自数据类，数据格式可以自由决定。
		 * @param request 用于请求用户审批的 i18n 消息，格式应如 '请求读取 README.md（$reason）'，中文文案应以 '请求' 开头。
		 * @param executing 用于工具执行过程中为用户显示的状态信息，应类似 '正在读取 README.md'，中文文案应以 '正在' 开头。
		 * @param cancelled 如果工具被取消，为用户显示的消息，例如 '读取 README.md 被取消'。
		 * @param rejected 如果工具被拒绝，为用户显示的消息，例如 '读取 README.md 被拒绝'，或 '读取 README.md 被拒绝：$reason'。
		 * @param failed 如果工具执行中抛出异常，为用户显示的消息，例如 '读取 README.md 失败：${e.message()}'。
		 * @param timeout 如果工具执行超时，为用户显示的消息，例如 '读取 README.md 超时：$elapsed'
		 */
		data class Ready(
			val result: JsonElement,
			
			val request: (reason: String) -> ToolPresentation,
			val executing: () -> ToolPresentation,
			val cancelled: () -> ToolPresentation,
			val rejected: (reason: String?) -> ToolPresentation,
			val failed: (e: Throwable) -> ToolPresentation,
			val timeout: (elapsed: Duration) -> ToolPresentation,
		) : ResolveResult
		
		/**
		 * 参数存在问题，驳回工具调用并使用 [reason] 作为工具消息。
		 *
		 * 工具不应该自行实现鉴权，可以在参数错误、目标不可访问等场景驳回调用。
		 *
		 * @param presentation 为用户显示的工具执行消息，例如 '读取文件失败，找不到 README.md'。
		 */
		data class Rejected(
			val reason: String,
			val presentation: ToolPresentation,
		) : ResolveResult
	}
	
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
	 * 工具响应，[result] 给 LLM 看，[success] / [presentation] 给用户看，[data] 给程序读。
	 */
	data class ToolOutput(
		/**
		 * 返回给 LLM 的内容，超出阈值部分会被截断，完整内容存入文件。
		 *
		 * 阈值由用户配置，默认 50 万字符，“字符”的语义是 [Char]。
		 */
		val result: String,
		/**
		 * 用于前端显示会话中的已执行工具，格式应如 '读取了 README.md'。
		 */
		val presentation: ToolPresentation,
		/**
		 * 结构化的数据，便于程序解析。
		 *
		 * @see io.github.autotweaker.api.types.tool.bash.BashOutput
		 */
		val data: JsonElement?,
		/**
		 * 工具执行是否成功。给用户看。
		 */
		val success: Boolean,
	)
}
