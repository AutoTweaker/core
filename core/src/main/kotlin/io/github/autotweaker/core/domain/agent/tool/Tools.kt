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
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.exception.notfound.ToolNotFoundException
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.tool.*
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
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Clock

class Tools(
	private val workspace: () -> Path,
	private val tools: ToolMap,
	activeTools: Set<String>,
	private val agentId: UUID,
) : Loggable, Traceable, I18nable {
	private val _activeTools = MutableStateFlow(activeTools)
	val activeTools: StateFlow<Set<String>> = _activeTools.asStateFlow()
	
	private val validator = ToolCallParser()
	
	fun activate(toolName: String, active: Boolean) {
		_activeTools.update { all ->
			if (active) all + toolName else all - toolName
		}
		log.debug("Changed tool activation  tool={}  activeTools={}  agentId={}", toolName, _activeTools.value, agentId)
	}
	
	
	suspend fun resolveToolCall(
		call: ChatMessage.AssistantMessage.ToolCall,
		provider: DependencyProvider,
	): ResolveResult {
		val meta = metaCache[call.name]?.first
		if (meta != null && !active(meta.name)) {
			val message = ToolSettings.ActiveMessage().format(
				meta.functions.joinToString(", ") { "${meta.name}-${it.name}" },
				meta.name
			)
			val presentation = listOf(UiBlock.Text(i18n(ToolI18n.Activation(), meta.name)))
			
			return ResolveResult.Activation(message, presentation)
				.andLog(log) {
					debug(
						"Resolved tool activation  agentId={}  callId={}  tool={}", agentId, call.id, call.name
					)
				}
		}
		val result = validator.validate(
			call.name, call.arguments,
			call.id, metaCache.filterKeys { active(it) }
		)
		return when (result) {
			is ToolCallParser.ValidationResult.Failure -> ResolveResult.ParseFailure(
				errorMessage = result.errorMessage,
				presentation = result.presentation
			)
			
			is ToolCallParser.ValidationResult.Success -> {
				val resolveResult = trace.catching {
					val tool = tools[result.toolName] ?: unreachable("Tool '${result.toolName}' not found")
					when (tool) {
						is CoreTool<ToolArgs> -> tool.resolve(provider, result.args)
						is Tool<ToolArgs> -> tool.resolve(result.args, workspace())
					}
				}.rethrow<SecretStoreLockedException>().rethrowCancellation()
					.getOrElse { e ->
						log.error("Failed tool call resolve  agentId={}  tool={}", agentId, result.toolName, e)
						Rejected(
							ToolSettings.ToolResolveError().format(e.message())
						) {
							text(i18n(ToolI18n.ResolveError(), result.toolName, e.message()))
						}
					}
				when (resolveResult) {
					is Tool.ResolveResult.Ready ->
						ResolveResult.NeedsApproval(
							toolName = result.toolName,
							reason = result.reason,
							validatedArgs = serializeValidatedArgs(result.toolName, result.args),
							resolveResult = resolveResult
						)
					
					is Tool.ResolveResult.Rejected ->
						ResolveResult.ResolveFailure(
							toolName = result.toolName,
							reason = result.reason,
							validatedArgs = serializeValidatedArgs(result.toolName, result.args),
							errorMessage = resolveResult.reason,
							presentation = resolveResult.presentation
						)
				}
			}
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
		request: JsonElement,
		provider: DependencyProvider,
		truncation: TruncationService,
		onToolOutput: (RuntimeOutput) -> Unit,
	): RuntimeContext.Message.Tool.Result {
		val tool = requireNotNull(tools[toolName])
		check(active(toolName)) { "Tool $toolName is not active" }
		
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
					is CoreTool<ToolArgs> -> tool.execute(provider, request, outputChannel)
					is Tool<ToolArgs> -> tool.execute(request, workspace(), outputChannel)
				}
			}.also { outputChannel.close() }.getOrThrow()
		}
		
		return RuntimeContext.Message.Tool.Result(
			id = UUID(),
			timestamp = Clock.System.now(),
			content = truncation(output.result, ToolSettings.MaxOutput().get()),
			data = output.data,
			presentation = output.presentation,
			status = if (output.success) ToolResultStatus.SUCCESS else ToolResultStatus.FAILURE,
		).andLog(log) {
			debug(
				"Completed tool execution  agentId={}  tool={}  success={}", agentId, toolName, output.success
			)
		}
	}
	
	private fun active(name: String): Boolean = name in _activeTools.value
	
	companion object {
		@Volatile
		private var metaCache: MetaCache = mapOf()
		private val toolNameCache = mutableMapOf<KClass<*>, String>()
		
		fun serializeValidatedArgs(toolName: String, args: ToolArgs): JsonElement =
			Json.encodeToJsonElement(
				metaCache[toolName].orThrow { ToolNotFoundException(toolName) }.second, args
			)
		
		fun deserializeValidatedArgs(toolName: String, args: JsonElement): ToolArgs =
			Json.decodeFromJsonElement(
				metaCache[toolName].orThrow { ToolNotFoundException(toolName) }.second, args
			)
		
		fun <T : ToolArgs> deserializeValidatedArgs(
			deserializer: KSerializer<T>, args: JsonElement
		): T = Json.decodeFromJsonElement(deserializer, args)
		
		suspend fun cacheMeta(tools: ToolMap): MetaCache = buildMap {
			tools.forEach {
				set(it.key, it.value.meta())
			}
		}
		
		fun getMetaCache(): Map<String, ToolMeta>? = metaCache.orNull()?.mapValues {
			it.value.first
		}
		
		suspend fun <T : ToolArgs> Tool<T>.name() =
			toolNameCache[this::class] ?: meta().first.name.also {
				toolNameCache[this::class] = it
			}
	}
}
