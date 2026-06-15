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
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.agent.ToolInfo
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.tool.ToolMeta
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

object ToolAssembler {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun assemble(
		tools: List<Tool<ToolArgs>>,
		toolInfo: List<ToolInfo>,
		service: SettingService,
	): List<ChatRequest.Tool>? {
		if (tools.isEmpty()) return null
		
		logger.debug("Started tool assembly  toolCount={}", tools.size)
		
		val reasonDescription = service.get(AgentToolSettings.ReasonEmptyError()).value
		val enableDesc = service.get(AgentToolSettings.EnableDescription()).value
		
		val activeNames = toolInfo.filter { it.active }.map { it.name }.toSet()
		val activeMetas = tools.filter { it.name in activeNames }.map { ToolMeta.build(it) }
		
		val activeTools = activeMetas.flatMap { meta ->
			meta.functions.map { func ->
				ChatRequest.Tool(
					name = "${meta.name}-${func.name}",
					description = func.description,
					parameters = func.parameters.toChatRequestParameters(reasonDescription),
				)
			}
		}
		
		val inactiveTools = tools.filter { it.name !in activeNames }.map { tool ->
			ChatRequest.Tool(
				name = tool.name,
				description = tool.description,
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
		
		return if (activeTools.isNotEmpty() || inactiveTools.isNotEmpty()) activeTools + inactiveTools
		else null
	}
	
	private fun Map<String, ToolMeta.Property>.toChatRequestParameters(
		reasonDescription: String
	): JsonElement = buildJsonObject {
		put("type", "object")
		putJsonObject("properties") {
			forEach { (name, prop) -> put(name, prop.toPropertyJson()) }
			put("reason", buildJsonObject {
				put("type", "string")
				put("description", reasonDescription)
			})
		}
		putJsonArray("required") {
			filter { it.value.required }.keys.forEach { add(it) }
			add("reason")
		}
	}
	
	private fun ToolMeta.Property.toPropertyJson(): JsonElement = buildJsonObject {
		put("description", description)
		valueType.fillJsonObject(this)
	}
	
	private fun ToolMeta.ValueType.fillJsonObject(builder: JsonObjectBuilder) {
		when (this) {
			is ToolMeta.ValueType.StringValue -> {
				builder.put("type", "string")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is ToolMeta.ValueType.NumberValue -> {
				builder.put("type", "number")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is ToolMeta.ValueType.IntegerValue -> {
				builder.put("type", "integer")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is ToolMeta.ValueType.BooleanValue -> builder.put("type", "boolean")
			is ToolMeta.ValueType.ArrayValue -> {
				builder.put("type", "array")
				builder.put("items", buildJsonObject { items.fillJsonObject(this) })
			}
			
			is ToolMeta.ValueType.ObjectValue -> {
				builder.put("type", "object")
				builder.putJsonObject("properties") {
					properties.forEach { (name, vt) ->
						put(name, buildJsonObject { vt.fillJsonObject(this) })
					}
				}
			}
			
			is ToolMeta.ValueType.MapValue -> {
				builder.put("type", "object")
				builder.put("additionalProperties", buildJsonObject { value.fillJsonObject(this) })
			}
			
			is ToolMeta.ValueType.AnyValue -> {}
			is ToolMeta.ValueType.OneOfValue -> {
				builder.put("type", "object")
				builder.putJsonArray("oneOf") {
					variants.forEach { (variantName, variantType) ->
						add(buildJsonObject {
							put("type", "object")
							putJsonObject("properties") {
								put("type", buildJsonObject { put("const", variantName) })
								if (variantType is ToolMeta.ValueType.ObjectValue) {
									variantType.properties.forEach { (propName, propType) ->
										put(propName, buildJsonObject { propType.fillJsonObject(this) })
									}
								}
							}
							putJsonArray("required") { add("type") }
						})
					}
				}
			}
		}
	}
}
