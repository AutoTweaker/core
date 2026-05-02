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

package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import kotlinx.serialization.json.*

class ToolCallValidator(
	private val tools: List<Tool>,
	private val settings: List<SettingItem>,
) {
	private val jsonErrorMessage: String = settings.find("core.agent.tool.response.json.error")
	private val propertyMissingMessage: String = settings.find("core.agent.tool.response.property.missing")
	private val propertyErrorMessage: String = settings.find("core.agent.tool.response.property.error")
	private val functionNotFoundMessage: String = settings.find("core.agent.tool.response.function.name.error")
	
	//输出格式
	sealed class ValidationResult {
		data class Success(
			val toolName: String,
			val functionName: String,
			val reason: String,
			val arguments: JsonObject,
		) : ValidationResult()
		
		data class Failure(
			val errorMessage: String,
		) : ValidationResult()
	}
	
	//主函数
	fun validate(toolCallName: String, argumentsJson: String): ValidationResult {
		//解析json参数
		val arguments = try {
			Json.parseToJsonElement(argumentsJson) as? JsonObject
				?: return ValidationResult.Failure(jsonErrorMessage.format("Invalid JSON object"))
		} catch (e: Exception) {
			return ValidationResult.Failure(jsonErrorMessage.format(e.message ?: "Unknown error"))
		}
		
		//解析工具名称与function名称
		val parts = toolCallName.split("_", limit = 2)
		if (parts.size != 2) {
			return ValidationResult.Failure(
				functionNotFoundMessage.format(toolCallName)
			)
		}
		
		val toolName = parts[0]
		val functionName = parts[1]
		
		//检查工具是否存在
		val tool = tools.find { it.resolveMeta(settings).name == toolName }
			?: return ValidationResult.Failure(
				functionNotFoundMessage.format(toolCallName)
			)
		val meta = tool.resolveMeta(settings)
		
		val function = meta.functions.find { it.name == functionName }
			?: return ValidationResult.Failure(
				functionNotFoundMessage.format(toolCallName)
			)
		
		//解析工具调用原因
		val reasonElement = arguments["reason"]
		if (reasonElement == null || reasonElement !is JsonPrimitive) {
			return ValidationResult.Failure(
				propertyMissingMessage.format(toolCallName, "reason")
			)
		}
		val reason = reasonElement.content
		
		//排除reason字段
		val otherArguments = JsonObject(arguments.filterKeys { it != "reason" })
		
		//提取必填字段
		val requiredParams = function.parameters.filter { it.value.required }
		for ((paramName, _) in requiredParams) {
			if (!otherArguments.containsKey(paramName)) {
				return ValidationResult.Failure(
					propertyMissingMessage.format(toolCallName, paramName)
				)
			}
		}
		
		//检查属性类型
		for ((paramName, paramDef) in function.parameters) {
			val paramValue = otherArguments[paramName] ?: continue
			if (!validateParameterType(paramValue, paramDef.valueType)) {
				val expectedType = getExpectedTypeName(paramDef.valueType)
				return ValidationResult.Failure(
					propertyErrorMessage.format(toolCallName, paramName, expectedType)
				)
			}
		}
		
		return ValidationResult.Success(
			toolName = toolName,
			functionName = functionName,
			reason = reason,
			arguments = otherArguments,
		)
	}
	
	//检查属性类型
	private fun validateParameterType(
		value: JsonElement,
		expectedType: Tool.Function.Property.ValueType
	): Boolean {
		return when (expectedType) {
			is Tool.Function.Property.ValueType.StringValue -> value is JsonPrimitive && value.isString
			is Tool.Function.Property.ValueType.NumberValue -> value is JsonPrimitive && !value.isString
			is Tool.Function.Property.ValueType.IntegerValue -> value is JsonPrimitive && !value.isString && value.content.toIntOrNull() != null
			is Tool.Function.Property.ValueType.BooleanValue -> value is JsonPrimitive && (value.content == "true" || value.content == "false")
			is Tool.Function.Property.ValueType.ArrayValue -> {
				if (value !is JsonArray) return false
				value.all { validateElementType(it, expectedType.items) }
			}
			
			is Tool.Function.Property.ValueType.ObjectValue -> {
				if (value !is JsonObject) return false
				expectedType.properties.all { (name, type) ->
					val fieldValue = value[name] ?: return false
					validateParameterType(fieldValue, type)
				}
			}
		}
	}
	
	private fun validateElementType(value: JsonElement, expectedType: Tool.Function.Property.ValueType): Boolean {
		return validateParameterType(value, expectedType)
	}
	
	private fun getExpectedTypeName(type: Tool.Function.Property.ValueType): String {
		return when (type) {
			is Tool.Function.Property.ValueType.StringValue -> "string"
			is Tool.Function.Property.ValueType.NumberValue -> "number"
			is Tool.Function.Property.ValueType.IntegerValue -> "integer"
			is Tool.Function.Property.ValueType.BooleanValue -> "boolean"
			is Tool.Function.Property.ValueType.ArrayValue -> "array"
			is Tool.Function.Property.ValueType.ObjectValue -> "object"
		}
	}
}
