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
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ToolCallValidator(
	private val tools: List<Tool>,
	private val service: SettingService,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
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
	
	fun validate(toolCallName: String, argumentsJson: String, callId: String = ""): ValidationResult {
		val arguments = try {
			Json.parseToJsonElement(argumentsJson) as? JsonObject ?: return ValidationResult.Failure(
				service.get(AgentToolSettings.JsonError()).value.format("Invalid JSON object")
			).also {
				logger.debug("Failed to validate tool call JSON  callId={}  name={}", callId, toolCallName)
			}
		} catch (e: Exception) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.JsonError()).value.format(e.message ?: "Unknown error")
			).also {
				logger.debug("Failed to parse tool call JSON  callId={}  name={}", callId, toolCallName)
			}
		}
		
		val parts = toolCallName.split("_", limit = 2)
		if (parts.size != 2) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
			).also { logger.debug("Failed to parse tool call name  callId={}  name={}", callId, toolCallName) }
		}
		
		val toolName = parts[0]
		val functionName = parts[1]
		
		val tool = tools.find { it.meta.name == toolName } ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
		).also { logger.debug("Failed to find tool  callId={}  name={}  tool={}", callId, toolCallName, toolName) }
		val meta = tool.meta
		
		val function = meta.functions.find { it.name == functionName } ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
		).also {
			logger.debug(
				"Failed to find function  callId={}  name={}  tool={}  function={}",
				callId,
				toolCallName,
				toolName,
				functionName
			)
		}
		
		val reasonElement = arguments["reason"]
		if (reasonElement == null || reasonElement !is JsonPrimitive) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.PropertyMissing()).value.format(toolCallName, "reason")
			).also {
				logger.debug(
					"Failed to validate tool call reason  callId={}  name={}  tool={}", callId, toolCallName, toolName
				)
			}
		}
		val reason = reasonElement.content
		
		val otherArguments = JsonObject(arguments.filterKeys { it != "reason" })
		
		val requiredParams = function.parameters.filter { it.value.required }
		for ((paramName, _) in requiredParams) {
			if (!otherArguments.containsKey(paramName)) {
				return ValidationResult.Failure(
					service.get(AgentToolSettings.PropertyMissing()).value.format(toolCallName, paramName)
				).also {
					logger.debug(
						"Failed to find required param  callId={}  name={}  tool={}  param={}",
						callId,
						toolCallName,
						toolName,
						paramName
					)
				}
			}
		}
		
		for ((paramName, paramDef) in function.parameters) {
			val paramValue = otherArguments[paramName] ?: continue
			if (!validateParameterType(paramValue, paramDef.valueType)) {
				val expectedType = getExpectedTypeName(paramDef.valueType)
				return ValidationResult.Failure(
					service.get(AgentToolSettings.PropertyError()).value.format(toolCallName, paramName, expectedType)
				).also {
					logger.debug(
						"Param type did not match  name={}  tool={}  param={}  expected={}",
						toolCallName,
						toolName,
						paramName,
						expectedType
					)
				}
			}
		}
		
		logger.debug(
			"Tool call validated  callId={}  name={}  tool={}  function={}",
			callId,
			toolCallName,
			toolName,
			functionName
		)
		return ValidationResult.Success(
			toolName = toolName,
			functionName = functionName,
			reason = reason,
			arguments = otherArguments,
		)
	}
	
	private fun validateParameterType(
		value: JsonElement, expectedType: Tool.Function.Property.ValueType
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
