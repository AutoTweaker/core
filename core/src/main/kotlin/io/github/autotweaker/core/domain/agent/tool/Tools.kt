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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.api.types.tool.ToolOutput
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.ToolMeta
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock

class Tools(
	toolInfo: List<ToolInfo>,
	private val service: SettingService,
	private val tools: List<Tool<ToolArgs>>,
	private val agentId: UUID
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	
	private val _toolInfo = MutableStateFlow(toolInfo)
	val toolInfo: StateFlow<List<ToolInfo>> = _toolInfo.asStateFlow()
	
	private val validator: ToolCallValidator = ToolCallValidator(service)
	
	//工具激活
	
	fun activate(toolName: String, active: Boolean) {
		_toolInfo.update { list -> list.map { if (it.name == toolName) it.copy(active = active) else it } }
		logger.debug("Changed tool activation  tool={}  active={}  agentId={}", toolName, active, agentId)
	}
	
	//工具调用
	
	suspend fun executeTool(
		toolName: String,
		callId: String,
		arguments: ToolArgs,
		provider: SimpleContainer,
		onToolOutput: suspend (AgentOutput) -> Unit,
	): AgentContext.Message.Tool.Result {
		val tool = tools.first { it.name == toolName }
		check(_toolInfo.value.first { it.name == tool.name }.active)
		
		logger.info("Started tool execution  agentId={}  tool={}", agentId, toolName)
		
		val outputChannel = Channel<Tool.RuntimeOutput>(Channel.UNLIMITED)
		val output = supervisorScope {
			val drainJob = launch {
				for (msg in outputChannel) {
					onToolOutput(AgentOutput.Tool(ToolOutput(toolName, callId, msg.content)))
				}
			}
			val result = try {
				when (tool) {
					is CoreTool -> tool.coreExec(provider, arguments, outputChannel)
					else -> tool.execute(arguments, outputChannel)
				}
			} catch (e: CancellationException) {
				trace.exception(e)
				throw e
			} catch (e: Exception) {
				trace.exception(e)
				logger.error("Failed tool execution  agentId={}  tool={}", agentId, toolName, e)
				Tool.ToolOutput(e.message ?: "Unknown error", false)
			}
			outputChannel.close()
			drainJob.join()
			result
		}
		
		logger.debug("Completed tool execution  agentId={}  tool={}  success={}", agentId, toolName, output.success)
		
		return AgentContext.Message.Tool.Result(
			content = output.result,
			timestamp = Clock.System.now(),
			status = if (output.success) ToolResultStatus.SUCCESS else ToolResultStatus.FAILURE,
		)
	}
	
	//工具解析
	
	suspend fun resolveToolCall(
		call: ChatMessage.AssistantMessage.ToolCall,
	): ToolCallResolveResult {
		if (_toolInfo.value.any { !it.active && it.name == call.name }) {
			val tool = tools.first { it.name == call.name }
			val meta = ToolMeta.build(tool)
			val message = service.get(AgentToolSettings.ActiveMessage()).value
				.format(meta.functions.joinToString(", ") { "${meta.name}-${it.name}" }, meta.name)
			logger.debug(
				"Resolved tool activation  agentId={}  callId={}  tool={}",
				agentId, call.id, call.name
			)
			return ToolCallResolveResult.Activation(message)
		}
		
		val activeTools = tools.filter { tool ->
			_toolInfo.value.any { info ->
				info.name == tool.name && info.active
			}
		}
		
		when (val result = validator.validate(call.name, call.arguments, call.id, activeTools)) {
			is ToolCallValidator.ValidationResult.Success -> {
				logger.debug(
					"Resolved tool call  agentId={}  callId={}  tool={}",
					agentId, call.id, call.name
				)
				return ToolCallResolveResult.NeedsApproval(result)
			}
			
			is ToolCallValidator.ValidationResult.Failure -> {
				logger.debug("Failed to resolve tool call  agentId={}  callId={}  tool={}", agentId, call.id, call.name)
				return ToolCallResolveResult.ParseFailure(result.errorMessage)
			}
		}
	}
	
	suspend fun assembleTools() =
		ToolAssembler.assemble(tools, _toolInfo.value, service)
	
	fun serializeValidatedArgs(toolName: String, args: ToolArgs): JsonElement {
		val tool = requireNotNull(tools.find { it.name == toolName })
		return Json.encodeToJsonElement(tool.argsSerializer, args)
	}
	
	companion object {
		suspend fun buildToolInfo(tool: Tool<ToolArgs>, active: Boolean): ToolInfo {
			val meta = ToolMeta.build(tool)
			return ToolInfo(
				tool.name, tool.description, meta.functions.map { it.name }, active
			)
		}
	}
}
