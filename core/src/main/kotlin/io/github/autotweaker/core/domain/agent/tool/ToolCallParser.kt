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
import io.github.autotweaker.api.tool.ToolArgs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ToolCallParser : Loggable, Traceable, Settable {
	sealed class ValidationResult<out Args : ToolArgs> : Settable {
		data class Success<Args : ToolArgs>(
			val toolName: String,
			val reason: String,
			val args: Args,
		) : ValidationResult<Args>()
		
		data class Failure(
			val errorMessage: String,
		) : ValidationResult<Nothing>()
	}
	
	fun validate(
		toolCallName: String,
		argumentsJson: String,
		callId: String,
		metaCache: MetaCache,
	): ValidationResult<*> {
		val (toolName, functionName) = resolveCallName(toolCallName)
			?.takeIf { result ->
				metaCache[result.first]?.first?.functions?.any { function ->
					function.name == result.second
				} == true
			}
			?: return ValidationResult.Failure(
				setting(AgentToolSettings.FunctionNameError()).format(toolCallName)
			).andLog(log) {
				debug("Failed tool call name parsing  callId={}  name={}", callId, toolCallName)
			}
		
		val arguments = trace.catching {
			Json.parseToJsonElement(argumentsJson) as? JsonObject
		}.getOrElse { e ->
			return ValidationResult.Failure(
				setting(AgentToolSettings.JsonError()).format(e.message ?: "Unknown error")
			).andLog(log) {
				debug("Failed tool call JSON parsing  callId={}  name={}", callId, toolCallName)
			}
		} ?: return ValidationResult.Failure(
			setting(AgentToolSettings.JsonError()).format("Invalid JSON object")
		).andLog(log) {
			debug("Failed tool call JSON validation  callId={}  name={}", callId, toolCallName)
		}
		
		val reasonElement = arguments["reason"]
		if (reasonElement == null || reasonElement !is JsonPrimitive) {
			return ValidationResult.Failure(
				setting(AgentToolSettings.PropertyMissing()).format(toolCallName, "reason")
			).andLog(log) {
				debug(
					"Failed tool call validation reason  callId={}  name={}  tool={}", callId, toolCallName, toolName
				)
			}
		}
		val reason = reasonElement.content
		
		if (reason.isBlank() || reason.length < setting(AgentToolSettings.ReasonLength()))
			return ValidationResult.Failure(setting(AgentToolSettings.ReasonEmptyError()))
		
		val argsSerializer = checkNotNull(metaCache[toolName]).second
		val deserializationJson = JsonObject(
			arguments.filterKeys { it != "reason" } + ("type" to JsonPrimitive(functionName))
		)
		
		val args = trace.catching {
			Json.decodeFromJsonElement(argsSerializer, deserializationJson)
		}.getOrElse { e ->
			return ValidationResult.Failure(
				setting(AgentToolSettings.DeserializationError()).format(toolCallName, e.message)
			).andLog(log) {
				debug(
					"Failed tool call arg deserialization  callId={}  name={}  tool={}  error={}",
					callId, toolCallName, toolName, e.message
				)
			}
		}
		
		log.debug(
			"Validated tool call  callId={}  name={}  tool={}  function={}",
			callId, toolCallName, toolName, functionName
		)
		return ValidationResult.Success(
			toolName = toolName,
			reason = reason,
			args = args,
		)
	}
	
	private fun resolveCallName(callName: String): Pair<String, String>? {
		val parts = callName.split("-")
		if (parts.size != 2) return null
		return parts[0] to parts[1]
	}
}
