package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.workspace.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

class Tools(settings: List<SettingItem>) {
	private val _settings = settings
	private val enableDescription: String = settings.find("core.agent.tool.description.enable")
	private val enabledMessage: String = settings.find("core.agent.tool.response.active")
	
	//存储工具状态
	data class Entry(
		val tool: Tool,
		var active: Boolean = false,
	)
	
	//存储工具列表
	private val _entries = mutableListOf<Entry>()
	
	@Suppress("unused")
	val entries: List<Entry> get() = _entries
	
	//添加工具
	fun add(tool: Tool) {
		_entries.add(Entry(tool))
	}
	
	//用于参数校验的对象
	private val _validator: ToolCallValidator
		get() = ToolCallValidator(
			_entries.filter { it.active }.map { it.tool },
			_settings,
		)
	
	//解析工具调用
	fun resolveToolCalls(
		calls: List<AgentContext.CurrentRound.PendingToolCall>,
	): List<ToolCallResolveResult> {
		return calls.map { call ->
			//调用参数解析器
			when (val validated = _validator.validate(call.name, call.arguments)) {
				//解析失败
				is ToolCallValidator.ValidationResult.Failure ->
					ToolCallResolveResult.ParseFailure(call.callId, validated.errorMessage)
				//解析成功
				is ToolCallValidator.ValidationResult.Success ->
					ToolCallResolveResult.NeedsApproval(call.callId, validated)
			}
		}
	}
	
	//解析输出
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
	
	//调用工具
	suspend fun executeTool(
		result: ToolCallValidator.ValidationResult.Success,
		call: AgentContext.CurrentRound.PendingToolCall,
		provider: SimpleContainer,
		workspace: Workspace,
	): AgentContext.Message.Tool {
		//匹配工具实现
		val entry = _entries.first { it.tool.resolveMeta(_settings).name == result.toolName }
		
		//工具未激活
		if (!entry.active) {
			//激活
			entry.active = true
			//读取工具参数
			val meta = entry.tool.resolveMeta(_settings)
			//返回激活成功的消息
			return AgentContext.Message.Tool(
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
		}
		
		//构建工具请求
		val toolInput = Tool.ToolInput(
			functionName = result.functionName,
			arguments = result.arguments,
			provider = provider,
			settings = _settings,
			workspace = workspace,
		)
		
		//调用工具
		val output = try {
			entry.tool.execute(toolInput)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Tool.ToolOutput(e.message ?: "Unknown error", false)
		}
		//返回结果
		return AgentContext.Message.Tool(
			name = call.name,
			call = AgentContext.Message.Tool.Call(
				arguments = call.arguments,
				reason = call.reason,
				timestamp = call.timestamp,
				model = call.model,
			),
			callId = call.callId,
			result = AgentContext.Message.Tool.Result(
				content = output.result,
				timestamp = Clock.System.now(),
				status = if (output.success) AgentContext.Message.Tool.Result.Status.SUCCESS
				else AgentContext.Message.Tool.Result.Status.FAILURE,
			),
		)
	}
	
	//获取工具参数
	fun assembleTools(): List<ChatRequest.Tool>? {
		//提取已激活工具
		val activeTools = _entries.filter { it.active }.map { it.tool }
		//构建工具参数
		val active = ToolAssembler.assemble(activeTools, _settings)
		
		//未激活工具构建特殊function
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
		
		//返回参数列表
		return if (active != null || inactive != null) {
			(active ?: emptyList()) + (inactive ?: emptyList())
		} else {
			null
		}
	}
}
