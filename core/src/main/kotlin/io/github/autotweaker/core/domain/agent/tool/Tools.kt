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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.tool.toolFail
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.ToolOutput
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Clock

class Tools(
	private val tools: ToolMap,
	activeTools: Set<String>,
	private val agentId: UUID,
) : Loggable, Traceable, Settable {
	private val _activeTools = MutableStateFlow(activeTools)
	val activeTools: StateFlow<Set<String>> = _activeTools.asStateFlow()
	
	private val validator = ToolCallParser()
	
	fun activate(toolName: String, active: Boolean) {
		_activeTools.update { all ->
			if (active) all + toolName else all.filterNot { it == toolName }.toSet()
		}
		log.debug("Changed tool activation  tool={}  activeTools={}  agentId={}", toolName, _activeTools.value, agentId)
	}
	
	
	fun resolveToolCall(
		call: ChatMessage.AssistantMessage.ToolCall,
	): ToolCallResolveResult {
		val meta = metaCache[call.name]?.first
		if (meta != null && !active(call.name)) {
			val message = setting(AgentToolSettings.ActiveMessage()).format(
				meta.functions.joinToString(", ") { "${meta.name}-${it.name}" },
				meta.name
			)
			
			return ToolCallResolveResult.Activation(message).andLog(log) {
				debug(
					"Resolved tool activation  agentId={}  callId={}  tool={}", agentId, call.id, call.name
				)
			}
		}
		val result = validator.validate(
			call.name, call.arguments, call.id, metaCache
		)
		return when (result) {
			is ToolCallParser.ValidationResult.Success -> ToolCallResolveResult.NeedsApproval(result)
			is ToolCallParser.ValidationResult.Failure -> ToolCallResolveResult.ParseFailure(result.errorMessage)
		}
	}
	
	suspend fun assembleTools(): List<ChatRequest.Tool>? {
		//缓存meta，此处为请求LLM前，确保每次请求前刷新
		metaCache = cacheMeta(tools)
		return ToolAssembler.assemble(metaCache, ::active)
	}
	
	suspend fun executeTool(
		toolName: String,
		callId: String,
		arguments: ToolArgs,
		provider: DependencyProvider,
		truncation: TruncationService,
		onToolOutput: (RuntimeOutput) -> Unit,
	): RuntimeContext.Message.Tool.Result {
		val tool = requireNotNull(tools[toolName])
		check(active(toolName))
		
		log.info("Started tool execution  agentId={}  tool={}", agentId, toolName)
		
		val outputChannel = Channel<Tool.RuntimeOutput>(Channel.UNLIMITED)
		val output = supervisorScope {
			launch {
				outputChannel.consumeEach {
					onToolOutput(RuntimeOutput.Tool(ToolOutput(toolName, callId, it.content, it.type)))
				}
			}
			trace.catching {
				when (tool) {
					is CoreTool<ToolArgs> -> tool.coreExec(provider, arguments, outputChannel)
					is Tool<ToolArgs> -> tool.execute(arguments, outputChannel)
				}
			}.also { outputChannel.close() }.rethrow<SecretStoreLockedException>().rethrowCancellation()
				.getOrElse { e ->
					log.error("Failed tool execution  agentId={}  tool={}", agentId, toolName, e)
					(e.message ?: "Unknown error").toolFail()
				}
		}
		
		return RuntimeContext.Message.Tool.Result(
			content = truncation(output.result, setting(AgentToolSettings.MaxOutput())),
			timestamp = Clock.System.now(),
			status = if (output.success) ToolResultStatus.SUCCESS else ToolResultStatus.FAILURE,
		).andLog(log) {
			debug(
				"Completed tool execution  agentId={}  tool={}  success={}", agentId, toolName, output.success
			)
		}
	}
	
	private fun active(name: String): Boolean = name in _activeTools.value
	
	companion object {
		private var metaCache: MetaCache = mapOf()
		private val toolNameCache = mutableMapOf<KClass<*>, String>()
		
		fun serializeValidatedArgs(toolName: String, args: ToolArgs): JsonElement =
			Json.encodeToJsonElement(requireNotNull(metaCache[toolName]).second, args)
		
		fun deserializeValidatedArgs(toolName: String, args: JsonElement): ToolArgs =
			Json.decodeFromJsonElement(requireNotNull(metaCache[toolName]).second, args)
		
		fun <T : ToolArgs> deserializeValidatedArgs(
			deserializer: KSerializer<T>, args: JsonElement
		): T = Json.decodeFromJsonElement(deserializer, args)
		
		suspend fun cacheMeta(tools: ToolMap): MetaCache = buildMap {
			tools.forEach {
				set(it.key, it.value.meta())
			}
		}
		
		fun getMetaCache(): Map<String, ToolMeta>? = metaCache.orNull()?.mapValues { it.value.first }
		
		suspend fun <T : ToolArgs> Tool<T>.name() =
			toolNameCache[this::class] ?: meta().first.name.also {
				toolNameCache[this::class] = it
			}
	}
}
