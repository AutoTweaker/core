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

import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.core.domain.tool.ToolMeta
import io.github.autotweaker.core.domain.tool.ToolMeta.Companion.toSnakeCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.json.*
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.trace

class ToolCallValidator(
	private val service: SettingService,
) : Loggable, Traceable {
	
	@OptIn(ExperimentalSerializationApi::class)
	private val json = Json {
		namingStrategy = JsonNamingStrategy.SnakeCase
	}
	
	sealed class ValidationResult<out Args : ToolArgs> {
		data class Success<Args : ToolArgs>(
			val toolName: String,
			val reason: String,
			val args: Args,
		) : ValidationResult<Args>()
		
		data class Failure(
			val errorMessage: String,
		) : ValidationResult<Nothing>()
	}
	
	@OptIn(ExperimentalSerializationApi::class)
	fun validate(
		toolCallName: String,
		argumentsJson: String,
		callId: String,
		tools: List<Tool<ToolArgs>>
	): ValidationResult<*> {
		val arguments = trace.catching {
			Json.parseToJsonElement(argumentsJson) as? JsonObject
		}.getOrElse { e ->
			return ValidationResult.Failure(
				service.get(AgentToolSettings.JsonError()).value.format(e.message ?: "Unknown error")
			).also {
				log.debug("Failed tool call JSON parsing  callId={}  name={}", callId, toolCallName)
			}
		} ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.JsonError()).value.format("Invalid JSON object")
		).also {
			log.debug("Failed tool call JSON validation  callId={}  name={}", callId, toolCallName)
		}
		
		val parts = toolCallName.split("-", limit = 2)
		if (parts.size != 2) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
			).andLog(log) { debug("Failed tool call name parsing  callId={}  name={}", callId, toolCallName) }
		}
		
		val toolName = parts[0]
		val functionName = parts[1]
		
		val tool = tools.find { it.name == toolName } ?: return ValidationResult.Failure(
			service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
		).andLog(log) { debug("Failed tool lookup  callId={}  name={}  tool={}", callId, toolCallName, toolName) }
		
		val reasonElement = arguments["reason"]
		if (reasonElement == null || reasonElement !is JsonPrimitive) {
			return ValidationResult.Failure(
				service.get(AgentToolSettings.PropertyMissing()).value.format(toolCallName, "reason")
			).also {
				log.debug(
					"Failed tool call validation reason  callId={}  name={}  tool={}", callId, toolCallName, toolName
				)
			}
		}
		val reason = reasonElement.content
		if (reason.isBlank()) return ValidationResult.Failure(service.get(AgentToolSettings.ReasonEmptyError()).value)
		
		val otherArguments = JsonObject(arguments.filterKeys { it != "reason" })
		val deserializationJson = if (tool.argsSerializer.descriptor.kind == PolymorphicKind.SEALED) {
			val sealedDesc = tool.argsSerializer.descriptor.getElementDescriptor(1)
			val typeName = (0 until sealedDesc.elementsCount)
				.map { sealedDesc.getElementName(it) }
				.find { it.substringAfterLast('.').toSnakeCase() == functionName }
				?: return ValidationResult.Failure(
					service.get(AgentToolSettings.FunctionNameError()).value.format(toolCallName)
				).also {
					log.debug(
						"Failed sealed subclass lookup  callId={}  name={}  function={}",
						callId,
						toolCallName,
						functionName
					)
				}
			JsonObject(otherArguments + ("type" to JsonPrimitive(typeName)))
		} else {
			otherArguments
		}
		
		val typeMapping = ToolMeta.buildTypeMapping(tool)
		val finalJson = if (typeMapping.isNotEmpty()) {
			val precisePaths = typeMapping.flatMap { sealedPath ->
				expandPaths(deserializationJson, sealedPath.segments, sealedPath.typeMap)
			}
			precisePaths.fold(deserializationJson) { current, precisePath ->
				applyPath(current, precisePath)
			}
		} else {
			deserializationJson
		}
		
		val args = trace.catching {
			json.decodeFromJsonElement(tool.argsSerializer, finalJson)
		}.getOrElse { e ->
			return ValidationResult.Failure(
				service.get(AgentToolSettings.DeserializationError()).value.format(toolCallName, e.message)
			).also {
				log.debug(
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
	
	private data class PrecisePath(
		val segments: List<Any>,
		val typeMap: Map<String, String>,
	)
	
	private fun expandPaths(
		json: JsonObject,
		segments: List<String>,
		typeMap: Map<String, String>,
	): List<PrecisePath> = expandOne(json, segments, typeMap)
	
	private fun expandOne(
		element: JsonElement,
		remaining: List<String>,
		typeMap: Map<String, String>,
	): List<PrecisePath> {
		if (remaining.isEmpty()) return listOf(PrecisePath(emptyList(), typeMap))
		val segment = remaining[0]
		val rest = remaining.drop(1)
		val jsonObject = element as? JsonObject ?: return emptyList()
		val child = jsonObject[segment]
		if (child == null) {
			if (rest.isEmpty()) {
				return jsonObject.keys.map { key -> PrecisePath(listOf(segment, key), typeMap) }
			}
			return jsonObject.values.flatMapIndexed { index, value ->
				expandOne(value, rest, typeMap).map { it.prependSegment(segment, index) }
			}
		}
		return when (child) {
			is JsonArray -> {
				if (rest.isEmpty()) {
					List(child.size) { index -> PrecisePath(listOf(segment, index), typeMap) }
				} else {
					child.flatMapIndexed { index, item ->
						expandOne(item, rest, typeMap).map { it.prependSegment(segment, index) }
					}
				}
			}
			
			is JsonObject -> {
				if (rest.isEmpty()) {
					if ("type" in child) {
						listOf(PrecisePath(listOf(segment), typeMap))
					} else {
						child.keys.map { key -> PrecisePath(listOf(segment, key), typeMap) }
					}
				} else {
					expandOne(child, rest, typeMap).map { it.prependSegment(segment) }
				}
			}
			
			else -> emptyList()
		}
	}
	
	private fun PrecisePath.prependSegment(vararg segments: Any): PrecisePath =
		copy(segments = segments.toList() + this.segments)
	
	private fun applyPath(json: JsonObject, precisePath: PrecisePath): JsonObject {
		if (precisePath.segments.isEmpty()) return json
		val rootKey = precisePath.segments[0] as? String ?: return json
		val remaining = precisePath.segments.drop(1)
		val child = json[rootKey] ?: return json
		val replaced = replaceAtPath(child, remaining, precisePath.typeMap)
		return JsonObject(json + (rootKey to replaced))
	}
	
	private fun replaceAtPath(
		element: JsonElement,
		path: List<Any>,
		typeMap: Map<String, String>,
	): JsonElement {
		if (path.isEmpty()) {
			val obj = element as? JsonObject ?: return element
			val shortName = (obj["type"] as? JsonPrimitive)?.content ?: return element
			val fqcn = typeMap[shortName] ?: return element
			return JsonObject(obj + ("type" to JsonPrimitive(fqcn)))
		}
		val segment = path[0]
		val remaining = path.drop(1)
		return when (element) {
			is JsonArray -> {
				val index = segment as? Int ?: return element
				JsonArray(element.mapIndexed { i, item ->
					if (i == index) replaceAtPath(item, remaining, typeMap) else item
				})
			}
			
			is JsonObject -> {
				val key = segment as? String ?: return element
				val child = element[key] ?: return element
				JsonObject(element + (key to replaceAtPath(child, remaining, typeMap)))
			}
			
			else -> element
		}
	}
}