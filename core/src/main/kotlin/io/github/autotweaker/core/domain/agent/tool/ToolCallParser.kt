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
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.tool.ToolSettings.ACTIVE_TOOL_NAME
import io.github.autotweaker.core.domain.agent.tool.ToolSettings.DEFAULT_FUNCTION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ToolCallParser : Loggable, Traceable, I18nable {
	sealed class ValidationResult {
		data class Success(
			val toolName: String,
			val reason: String,
			val args: ToolArgs,
		) : ValidationResult()
		
		data class Failure(
			val errorMessage: String,
			val presentation: ToolPresentation,
		) : ValidationResult()
	}
	
	fun validate(
		toolCallName: String,
		argumentsJson: String,
		callId: String,
		metaCache: MetaCache,
	): ValidationResult {
		val resolvedName = resolveCallName(toolCallName)
		if (resolvedName == null || metaCache[resolvedName.first]?.first?.functions?.none {
				it.name == resolvedName.second
			} ?: true) {
			if (resolvedName?.first == ACTIVE_TOOL_NAME && resolvedName.second == DEFAULT_FUNCTION)
				return ValidationResult.Failure(
					ToolSettings.ActiveNotFound().get(),
					listOf(UiBlock.Text(i18n(ToolI18n.NotFoundError(), toolCallName)))
				)
			return ValidationResult.Failure(
				ToolSettings.FunctionNameError().format(toolCallName),
				listOf(UiBlock.Text(i18n(ToolI18n.NotFoundError(), toolCallName)))
			).andLog(log) {
				debug("Failed tool call name parsing  callId={}  name={}", callId, toolCallName)
			}
		}
		val (toolName, functionName) = resolvedName
		
		val arguments = trace.catching {
			Json.parseToJsonElement(argumentsJson)
		}.getOrElse { e ->
			return ValidationResult.Failure(
				ToolSettings.JsonError().format(e.message ?: e.message()),
				listOf(UiBlock.Text(i18n(ToolI18n.JsonParseError(), toolName)))
			).andLog(log) {
				debug("Failed tool call JSON parsing  callId={}  name={}", callId, toolCallName)
			}
		}
		
		if (arguments !is JsonObject) return ValidationResult.Failure(
			ToolSettings.JsonError()
				.format("Expected JSON object, got ${arguments::class.simpleName ?: "Unknown"}"),
			listOf(UiBlock.Text(i18n(ToolI18n.JsonParseError(), toolName)))
		).andLog(log) {
			debug("Failed tool call JSON validation  callId={}  name={}", callId, toolCallName)
		}
		
		val reasonElement = arguments["reason"]
		if (reasonElement == null || reasonElement !is JsonPrimitive) {
			return ValidationResult.Failure(
				ToolSettings.PropertyMissing().format(toolCallName, "reason"),
				listOf(UiBlock.Text(i18n(ToolI18n.ArgumentsError(), toolName)))
			).andLog(log) {
				debug(
					"Failed tool call validation reason  callId={}  name={}  tool={}", callId, toolCallName, toolName
				)
			}
		}
		val reason = reasonElement.content
		
		if (reason.isBlank() || reason.length < ToolSettings.ReasonLength().get())
			return ValidationResult.Failure(
				ToolSettings.ReasonEmptyError().get(),
				listOf(UiBlock.Text(i18n(ToolI18n.ArgumentsError(), toolName)))
			)
		
		val argsSerializer = checkNotNull(metaCache[toolName]).second
		val deserializationJson = JsonObject(
			arguments.filterKeys { it != "reason" } + ("type" to JsonPrimitive(functionName))
		)
		
		val args = trace.catching {
			Json.decodeFromJsonElement(argsSerializer, deserializationJson)
		}.getOrElse { e ->
			return ValidationResult.Failure(
				ToolSettings.DeserializationError().format(toolCallName, e.message ?: e.message()),
				listOf(UiBlock.Text(i18n(ToolI18n.ArgumentsError(), toolName)))
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
}

fun resolveCallName(callName: String): Pair<String, String>? {
	val parts = callName.split("-")
	if (parts.size == 1) return parts[0] to DEFAULT_FUNCTION
	if (parts.size != 2) return null
	return parts[0] to parts[1]
}
