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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

class ToolCallValidator(
	private val tools: List<Tool<*>>,
	private val service: SettingService,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	@OptIn(ExperimentalSerializationApi::class)
	private val json = Json {
		namingStrategy = JsonNamingStrategy.SnakeCase
	}
	
	sealed class ValidationResult<out Args : Any> {
		data class Success<Args : Any>(
			val toolName: String,
			val functionName: String,
			val reason: String,
			val args: Args,
		) : ValidationResult<Args>()
		
		data class Failure(
			val errorMessage: String,
		) : ValidationResult<Nothing>()
	}
	
	@OptIn(ExperimentalSerializationApi::class)
	fun validate(toolCallName: String, argumentsJson: String, callId: String = ""): ValidationResult<*> {
		val arguments = runCatching {
			Json.parseToJsonElement(argumentsJson) as? JsonObject
		}.getOrElse { e ->
			return ValidationResult.Failure(
				service.get(AgentToolSettings.JsonError()).value.format(e.message ?: "Unknown error")
			).also {
				logger.debug("Failed to parse tool call JSON  callId={}  name={}", callId, toolCallName)
			}
		} ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.JsonError()).value.format("Invalid JSON object")
		).also {
			logger.debug("Failed to validate tool call JSON  callId={}  name={}", callId, toolCallName)
		}
		
		val parts = toolCallName.split("-", limit = 2)
		if (parts.size != 2) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
			).also { logger.debug("Failed to parse tool call name  callId={}  name={}", callId, toolCallName) }
		}
		
		val toolName = parts[0]
		val functionName = parts[1]
		
		val tool = tools.find { it.name == toolName } ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
		).also { logger.debug("Failed to find tool  callId={}  name={}  tool={}", callId, toolCallName, toolName) }
		
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
		val deserializationJson = if (tool.argsSerializer.descriptor.kind == PolymorphicKind.SEALED) {
			JsonObject(otherArguments + ("type" to JsonPrimitive(functionName)))
		} else {
			otherArguments
		}
		val args = runCatching {
			json.decodeFromJsonElement(tool.argsSerializer, deserializationJson)
		}.getOrElse { e ->
			return ValidationResult.Failure(
				service.get(AgentToolSettings.DeserializationError()).value.format(toolCallName, e.message)
			).also {
				logger.debug(
					"Failed to deserialize tool call args  callId={}  name={}  tool={}  error={}",
					callId, toolCallName, toolName, e.message
				)
			}
		}
		
		logger.debug(
			"Tool call validated  callId={}  name={}  tool={}  function={}",
			callId, toolCallName, toolName, functionName
		)
		return ValidationResult.Success(
			toolName = toolName,
			functionName = functionName,
			reason = reason,
			args = args,
		)
	}
}
