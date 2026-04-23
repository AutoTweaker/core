package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.Tool
import kotlinx.serialization.json.JsonPrimitive

object ToolAssembler {
	//入口方法
	fun assemble(tools: List<Tool<*, *>>, settings: List<SettingItem>): List<ChatRequest.Tool>? {
		if (tools.isEmpty()) return null
		
		val reasonDescription: String = settings.find("core.agent.tool.description.reason")
		
		return tools.flatMap { tool ->
			tool.functions.map { func ->
				ChatRequest.Tool(
					name = "${tool.name}_${func.name}",
					description = func.description,
					parameters = func.parameters.toChatRequestParameters(reasonDescription),
				)
			}
		}
	}
	
	//扩展方法，逐个转换
	private fun Map<String, Tool.Function.Property>.toChatRequestParameters(
		reasonDescription: String
	): ChatRequest.Tool.Parameters {
		val properties = mapValues { (_, prop) ->
			ChatRequest.Tool.Parameters.Property(
				//映射字段类型
				type = when (prop.value) {
					is Tool.Function.Property.Value.StringValue -> ChatRequest.Tool.Parameters.Property.Type.STRING
					is Tool.Function.Property.Value.NumberValue -> ChatRequest.Tool.Parameters.Property.Type.NUMBER
					is Tool.Function.Property.Value.IntegerValue -> ChatRequest.Tool.Parameters.Property.Type.INTEGER
					is Tool.Function.Property.Value.BooleanValue -> ChatRequest.Tool.Parameters.Property.Type.BOOLEAN
					is Tool.Function.Property.Value.ArrayValue -> ChatRequest.Tool.Parameters.Property.Type.ARRAY
					is Tool.Function.Property.Value.ObjectValue -> ChatRequest.Tool.Parameters.Property.Type.OBJECT
				},
				//映射描述
				description = prop.description,
				//映射枚举选项
				enum = when (val v = prop.value) {
					is Tool.Function.Property.Value.StringValue -> v.enum?.map {
						JsonPrimitive(it)
					}
					
					is Tool.Function.Property.Value.NumberValue -> v.enum?.map {
						JsonPrimitive(it)
					}
					
					is Tool.Function.Property.Value.IntegerValue -> v.enum?.map {
						JsonPrimitive(it)
					}
					
					else -> null
				},
			)
		}
		
		//注入reason字段
		val allProperties = properties + ("reason" to ChatRequest.Tool.Parameters.Property(
			type = ChatRequest.Tool.Parameters.Property.Type.STRING,
			description = reasonDescription,
		))
		
		//reason字段required
		val required = filter { it.value.required }.keys.toList() + "reason"
		return ChatRequest.Tool.Parameters(properties = allProperties, required = required)
	}
}
