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
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.api.types.tool.ToolOutput
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.coroutines.channels.Channel
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
import kotlin.time.Clock

class Tools(
	toolInfo: List<ToolInfo>,
	private val tools: List<Tool<*>>,
	private val agentId: UUID
) : Loggable, Traceable, Settable {
	
	private val _toolInfo = MutableStateFlow(toolInfo)
	val toolInfo: StateFlow<List<ToolInfo>> = _toolInfo.asStateFlow()
	
	private val validator: ToolCallParser = ToolCallParser()
	
	fun activate(toolName: String, active: Boolean) {
		_toolInfo.update { list -> list.map { if (it.name == toolName) it.copy(active = active) else it } }
		log.debug("Changed tool activation  tool={}  active={}  agentId={}", toolName, active, agentId)
	}
	
	suspend fun executeTool(
		toolName: String,
		callId: String,
		arguments: ToolArgs,
		provider: DependencyProvider,
		truncation: TruncationService,
		onToolOutput: (RuntimeOutput) -> Unit,
	): RuntimeContext.Message.Tool.Result {
		val tool = tools.first { it.meta().first.name == toolName }
		check(_toolInfo.value.first { it.name == tool.meta().first.name }.active)
		
		log.info("Started tool execution  agentId={}  tool={}", agentId, toolName)
		
		val outputChannel = Channel<Tool.RuntimeOutput>(Channel.UNLIMITED)
		val output = supervisorScope {
			val drainJob = launch {
				for ((content, _) in outputChannel) {
					onToolOutput(RuntimeOutput.Tool(ToolOutput(toolName, callId, content)))
				}
			}
			val result = trace.catching {
				@Suppress("UNCHECKED_CAST")
				when (tool) {
					is CoreTool -> (tool as CoreTool<ToolArgs>).coreExec(provider, arguments, outputChannel)
					else -> (tool as Tool<ToolArgs>).execute(arguments, outputChannel)
				}
			}.also { outputChannel.close() }
				.rethrow<SecretStoreLockedException>()
				.rethrowCancellation()
				.getOrElse { e ->
					log.error("Failed tool execution  agentId={}  tool={}", agentId, toolName, e)
					(e.message ?: "Unknown error").toolFail()
				}
			drainJob.join()
			return@supervisorScope result
		}
		
		return RuntimeContext.Message.Tool.Result(
			content = truncation(output.result, setting(AgentToolSettings.MaxOutput())),
			timestamp = Clock.System.now(),
			status = if (output.success) ToolResultStatus.SUCCESS else ToolResultStatus.FAILURE,
		).andLog(log) {
			debug(
				"Completed tool execution  agentId={}  tool={}  success={}",
				agentId,
				toolName,
				output.success
			)
		}
	}
	
	suspend fun resolveToolCall(
		call: ChatMessage.AssistantMessage.ToolCall,
	): ToolCallResolveResult {
		if (_toolInfo.value.any { !it.active && it.name == call.name }) {
			val tool = tools.first { it.meta().first.name == call.name }
			val (meta, _) = tool.meta()
			val message = setting(AgentToolSettings.ActiveMessage())
				.format(meta.functions.joinToString(", ") { "${meta.name}-${it.name}" }, meta.name)
			
			return ToolCallResolveResult.Activation(message)
				.andLog(log) {
					debug(
						"Resolved tool activation  agentId={}  callId={}  tool={}",
						agentId, call.id, call.name
					)
				}
		}
		
		val activeTools = tools.filter { tool ->
			_toolInfo.value.any { info ->
				info.name == tool.meta().first.name && info.active
			}
		}
		
		when (val result = validator.validate(call.name, call.arguments, call.id, activeTools)) {
			is ToolCallParser.ValidationResult.Success ->
				return ToolCallResolveResult.NeedsApproval(result)
					.andLog(log) {
						debug(
							"Resolved tool call  agentId={}  callId={}  tool={}",
							agentId, call.id, call.name
						)
					}
			
			is ToolCallParser.ValidationResult.Failure ->
				return ToolCallResolveResult.ParseFailure(result.errorMessage)
					.andLog(log) {
						debug(
							"Failed to resolve tool call  agentId={}  callId={}  tool={}",
							agentId,
							call.id,
							call.name
						)
					}
		}
	}
	
	suspend fun assembleTools() =
		ToolAssembler.assemble(tools, _toolInfo.value)
	
	suspend fun serializeValidatedArgs(toolName: String, args: ToolArgs): JsonElement {
		val tool = requireNotNull(tools.find { it.meta().first.name == toolName })
		val (_, serializer) = tool.meta()
		@Suppress("UNCHECKED_CAST")
		return Json.encodeToJsonElement(serializer as KSerializer<ToolArgs>, args)
	}
	
	companion object {
		suspend fun buildToolInfo(tool: Tool<*>, active: Boolean): ToolInfo {
			val (meta, _) = tool.meta()
			return ToolInfo(
				meta.name, meta.description, meta.functions.map { it.name }, active
			)
		}
	}
}
