package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.workspace.Workspace
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

class Tools(settings: List<SettingItem>) {
	private val _settings = settings
	private val enableDescription: String = settings.find("core.agent.tool.description.enable")
	private val enabledMessage: String = settings.find("core.agent.tool.response.active")
	
	data class Entry(
		val tool: Tool,
		var active: Boolean = false,
	)
	
	private val _entries = mutableListOf<Entry>()
	
	@Suppress("unused")
	val entries: List<Entry> get() = _entries
	
	fun add(tool: Tool) {
		_entries.add(Entry(tool))
	}
	
	
	private val _validator = ToolCallValidator(
		_entries.filter { it.active }.map { it.tool },
		_settings,
	)
	
	@Suppress("unused")
	fun resolveToolCalls(
		calls: List<AgentContext.CurrentRound.PendingToolCall>,
		workspace: Workspace,
	): ToolCallResolveResult {
		if (calls.isEmpty()) return ToolCallResolveResult.AutoApproved(emptyList())
		
		val results = calls.map { call ->
			call to _validator.validate(call.name, call.arguments)
		}
		
		val failures = results
			.filter { it.second is ToolCallValidator.ValidationResult.Failure }
			.map { it.first to (it.second as ToolCallValidator.ValidationResult.Failure).errorMessage }
		if (failures.isNotEmpty()) return ToolCallResolveResult.ParseFailure(failures)
		
		val validated = results.map {
			(it.second as ToolCallValidator.ValidationResult.Success)
		}
		
		val toolByName = _entries.associate { it.tool.resolveMeta(_settings).name to it.tool }
		val (autoApproved, needsApproval) = validated.partition { result ->
			val tool = toolByName[result.toolName] ?: return@partition false
			tool.isAutoApproval(result.functionName, result.arguments, workspace)
		}
		
		return if (needsApproval.isEmpty()) {
			ToolCallResolveResult.AutoApproved(autoApproved)
		} else {
			ToolCallResolveResult.NeedsApproval(needsApproval)
		}
	}
	
	sealed class ToolCallResolveResult {
		data class ParseFailure(
			val errors: List<Pair<AgentContext.CurrentRound.PendingToolCall, String>>
		) : ToolCallResolveResult()
		
		data class NeedsApproval(
			val validated: List<ToolCallValidator.ValidationResult.Success>
		) : ToolCallResolveResult()
		
		data class AutoApproved(
			val validated: List<ToolCallValidator.ValidationResult.Success>
		) : ToolCallResolveResult()
	}
	
	@Suppress("unused")
	suspend fun executeTools(
		calls: List<AgentContext.CurrentRound.PendingToolCall>,
		validated: List<ToolCallValidator.ValidationResult.Success>,
		provider: SimpleContainer,
		workspace: Workspace,
	): List<AgentContext.Message.Tool> {
		return calls.zip(validated).map { (call, result) ->
			val entry = _entries.first { it.tool.resolveMeta(_settings).name == result.toolName }
			
			if (!entry.active) {
				entry.active = true
				val meta = entry.tool.resolveMeta(_settings)
				AgentContext.Message.Tool(
					name = call.name,
					call = AgentContext.Message.Tool.Call(
						arguments = call.arguments,
						reason = call.reason,
						timestamp = call.timestamp,
						model = call.model,
					),
					callId = call.callId,
					result = AgentContext.Message.Tool.Result(
						content = enabledMessage.format(meta.name, meta.functions.size),
						timestamp = Clock.System.now(),
						status = AgentContext.Message.Tool.Result.Status.SUCCESS,
					),
				)
			} else {
				TODO("Active tool execution not yet implemented")
			}
		}
	}
	
	fun assembleTools(): List<ChatRequest.Tool>? {
		val activeTools = _entries.filter { it.active }.map { it.tool }
		val active = ToolAssembler.assemble(activeTools, _settings)
		
		val inactive = _entries
			.filter { !it.active }
			.map { it.tool }
			.takeIf { it.isNotEmpty() }
			?.map { tool ->
				val meta = tool.resolveMeta(_settings)
				ChatRequest.Tool(
					name = meta.name,
					description = meta.description,
					parameters = buildJsonObject {
						put("type", "object")
						put("properties", buildJsonObject {
							put("enable", buildJsonObject {
								put("type", "boolean")
								put("description", enableDescription)
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
