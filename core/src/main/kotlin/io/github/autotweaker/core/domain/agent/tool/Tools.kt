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
import io.github.autotweaker.api.types.agent.ToolOutput
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock

class Tools(private val service: SettingService) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	data class Entry(
		val tool: Tool,
		var active: Boolean = false,
		var consecutiveUnused: Int = 0,
	)
	
	private val _entries = mutableListOf<Entry>()
	
	val entries: List<Entry> get() = _entries
	
	fun add(tool: Tool, container: SimpleContainer) {
		if (tool is CoreTool) {
			tool.init(service, container)
		}
		logger.debug("Tool added  tool={}  functionCount={}", tool.meta.name, tool.meta.functions.size)
		_entries.add(Entry(tool))
	}
	
	private val _validator: ToolCallValidator
		get() = ToolCallValidator(
			_entries.filter { it.active }.map { it.tool },
			service,
		)
	
	fun resolveToolCalls(
		calls: List<AgentContext.CurrentRound.PendingToolCall>,
		agentId: UUID = UUID.randomUUID(),
	): List<ToolCallResolveResult> {
		val results = calls.map { call ->
			when (val validated = _validator.validate(call.name, call.arguments, call.callId)) {
				is ToolCallValidator.ValidationResult.Failure -> ToolCallResolveResult.ParseFailure(
					call.callId, validated.errorMessage
				).also {
					logger.debug(
						"Failed to parse tool call  agentId={}  callId={}  tool={}  error={}",
						agentId,
						call.callId,
						call.name,
						validated.errorMessage
					)
				}
				
				is ToolCallValidator.ValidationResult.Success -> ToolCallResolveResult.NeedsApproval(
					call.callId, validated
				).also {
					logger.debug("Tool call validated  agentId={}  callId={}  tool={}", agentId, call.callId, call.name)
				}
			}
		}
		logger.debug(
			"Tool calls resolved  agentId={}  success={}  failed={}",
			agentId,
			results.count { it is ToolCallResolveResult.NeedsApproval },
			results.count { it is ToolCallResolveResult.ParseFailure })
		return results
	}
	
	sealed class ToolCallResolveResult {
		abstract val callId: String
		
		data class ParseFailure(
			override val callId: String,
			val errorMessage: String,
		) : ToolCallResolveResult()
		
		data class NeedsApproval(
			override val callId: String,
			val result: ToolCallValidator.ValidationResult.Success,
		) : ToolCallResolveResult()
	}
	
	suspend fun executeTool(
		result: ToolCallValidator.ValidationResult.Success,
		call: AgentContext.CurrentRound.PendingToolCall,
		agentId: UUID = UUID.randomUUID(),
		onToolActivated: (suspend (List<Tool>) -> Unit)? = null,
		onToolDeactivated: (suspend (List<Tool>) -> Unit)? = null,
		onToolOutput: (suspend (AgentOutput.Tool) -> Unit)? = null,
	): AgentContext.Message.Tool {
		val entry = _entries.first { it.tool.meta.name == result.toolName }
		val tool = entry.tool
		
		logger.debug(
			"Tool execution started  tool={}  function={}  reason={}  active={}",
			result.toolName,
			result.functionName,
			result.reason,
			entry.active
		)
		
		val threshold = service.get(AgentToolSettings.DeactivationThreshold()).value
		if (threshold > 0) {
			entry.consecutiveUnused = 0
			for (other in _entries) {
				if (other.active && other != entry) {
					other.consecutiveUnused++
				}
			}
			val toDeactivate = _entries.filter {
				it.active && it != entry && it.consecutiveUnused > threshold
			}
			if (toDeactivate.isNotEmpty()) {
				for (deact in toDeactivate) {
					logger.debug(
						"Tool deactivated  tool={}  consecutiveUnused={}  threshold={}",
						deact.tool.meta.name,
						deact.consecutiveUnused,
						threshold
					)
					deact.active = false
					deact.consecutiveUnused = 0
				}
				onToolDeactivated?.invoke(_entries.filter { it.active }.map { it.tool })
			}
		}
		
		if (!entry.active) {
			logger.debug("Tool activated  tool={}  function={}", result.toolName, result.functionName)
			entry.active = true
			onToolActivated?.invoke(_entries.filter { it.active }.map { it.tool })
			return AgentContext.Message.Tool(
				name = call.name,
				call = AgentContext.Message.Tool.Call(
					assistantMessageId = call.assistantMessageId,
					arguments = call.arguments,
					reason = call.reason,
					timestamp = call.timestamp,
					modelId = call.modelId,
				),
				callId = call.callId,
				result = AgentContext.Message.Tool.Result(
					content = service.get(AgentToolSettings.ActiveMessage()).value.format(
						tool.meta.name, tool.meta.functions.size
					),
					timestamp = Clock.System.now(),
					status = ToolResultStatus.SUCCESS,
				),
			)
		}
		
		val outputChannel = Channel<Tool.RuntimeOutput>(Channel.UNLIMITED)
		
		val toolInput = Tool.ToolInput(
			functionName = result.functionName,
			arguments = result.arguments,
			service = service,
			outputChannel = outputChannel,
		)
		
		val output = coroutineScope {
			val drainJob = launch {
				for (msg in outputChannel) {
					onToolOutput?.invoke(AgentOutput.Tool(ToolOutput(call.name, call.callId, msg.content)))
				}
			}
			val result = try {
				tool.execute(toolInput)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				logger.error(
					"Failed to execute tool  agentId={}  tool={}  function={}",
					agentId,
					result.toolName,
					result.functionName,
					e
				)
				Tool.ToolOutput(e.message ?: "Unknown error", false)
			}
			outputChannel.close()
			drainJob.join()
			result
		}
		
		logger.debug(
			"Tool execution completed  tool={}  function={}  success={}",
			result.toolName,
			result.functionName,
			output.success
		)
		
		return AgentContext.Message.Tool(
			name = call.name,
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = call.assistantMessageId,
				arguments = call.arguments,
				reason = call.reason,
				timestamp = call.timestamp,
				modelId = call.modelId,
			),
			callId = call.callId,
			result = AgentContext.Message.Tool.Result(
				content = output.result,
				timestamp = Clock.System.now(),
				status = if (output.success) ToolResultStatus.SUCCESS
				else ToolResultStatus.FAILURE,
			),
		)
	}
	
	fun assembleTools(): List<ChatRequest.Tool>? {
		val activeTools = _entries.filter { it.active }.map { it.tool }
		val active = ToolAssembler.assemble(activeTools, service)
		
		val enableDesc = service.get(AgentToolSettings.EnableDescription()).value
		val inactive = _entries.filter { !it.active }.map { it.tool }.takeIf { it.isNotEmpty() }?.map { tool ->
			ChatRequest.Tool(
				name = tool.meta.name,
				description = tool.meta.description,
				parameters = buildJsonObject {
					put("type", "object")
					put("properties", buildJsonObject {
						put("enable", buildJsonObject {
							put("type", "boolean")
							put("description", enableDesc)
						})
					})
				},
			)
		}
		
		return if (active != null || inactive != null) {
			(active ?: emptyList()) + (inactive ?: emptyList())
		} else {
			null
		}
	}
}
