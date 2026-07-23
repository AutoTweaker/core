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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.api.types.tool.ToolMeta
import kotlinx.serialization.json.*

object ToolAssembler : Loggable, Settable {
	suspend fun assemble(
		tools: List<Tool<*>>,
		toolInfo: List<ToolInfo>,
	): List<ChatRequest.Tool>? {
		if (tools.isEmpty()) return null
		
		log.debug("Started tool assembly  toolCount={}", tools.size)
		
		val reasonDescription = setting(AgentToolSettings.ReasonEmptyError())
		val enableDesc = setting(AgentToolSettings.EnableDescription())
		
		val activeNames = toolInfo.filter { it.active }.map { it.name }.toSet()
		
		val activeTools = tools.filter { it.meta().first.name in activeNames }.flatMap { tool ->
			val (meta, _) = tool.meta()
			meta.functions.map { func ->
				ChatRequest.Tool(
					name = "${meta.name}-${func.name}",
					description = func.description,
					parameters = func.parameters.toChatRequestParameters(reasonDescription),
				)
			}
		}
		
		val inactiveTools = tools.filter { it.meta().first.name !in activeNames }.map { tool ->
			val (meta, _) = tool.meta()
			ChatRequest.Tool(
				name = meta.name,
				description = meta.description,
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
		
		return (activeTools + inactiveTools).orNull()
	}
	
	private fun List<ToolMeta.Prop>.toChatRequestParameters(
		reasonDescription: String
	): JsonElement = buildJsonObject {
		put("type", "object")
		putJsonObject("properties") {
			forEach { (name, type, _, description) -> put(name, type.toPropertyJson(description)) }
			put("reason", buildJsonObject {
				put("type", "string")
				put("description", reasonDescription)
			})
		}
		putJsonArray("required") {
			filter { it.required }.forEach { add(it.name) }
			add("reason")
		}
	}
	
	private fun ToolMeta.Type.toPropertyJson(description: String): JsonElement = buildJsonObject {
		put("description", description)
		fillJsonObject(this)
	}
	
	private fun ToolMeta.Type.fillJsonObject(builder: JsonObjectBuilder) {
		when (this) {
			is ToolMeta.Type.TString -> {
				builder.put("type", "string")
			}
			
			is ToolMeta.Type.TInt, is ToolMeta.Type.TLong -> {
				builder.put("type", "integer")
			}
			
			is ToolMeta.Type.TDouble -> {
				builder.put("type", "number")
			}
			
			is ToolMeta.Type.TBoolean -> {
				builder.put("type", "boolean")
			}
			
			is ToolMeta.Type.TList -> {
				builder.put("type", "array")
				builder.put("items", buildJsonObject { element.fillJsonObject(this) })
			}
			
			is ToolMeta.Type.TMap -> {
				builder.put("type", "object")
				builder.put("additionalProperties", buildJsonObject { value.fillJsonObject(this) })
			}
			
			is ToolMeta.Type.OneOf -> {
				builder.put("type", "object")
				builder.putJsonArray("oneOf") {
					for ((name, description, properties) in variants) {
						add(buildJsonObject {
							put("type", "object")
							putJsonObject("properties") {
								put("type", buildJsonObject {
									put("const", name)
									put("description", description)
								})
								for ((propName, propType, _, propDesc) in properties) {
									put(propName, buildJsonObject {
										put("description", propDesc)
										propType.fillJsonObject(this)
									})
								}
							}
							putJsonArray("required") {
								add("type")
								properties.filter { it.required }.forEach { add(it.name) }
							}
						})
					}
				}
			}
			
			is ToolMeta.Type.Obj -> {
				builder.put("type", "object")
				builder.putJsonObject("properties") {
					for ((name, type, _, description) in properties) {
						put(name, buildJsonObject {
							put("description", description)
							type.fillJsonObject(this)
						})
					}
				}
				val requiredProps = properties.filter { it.required }.map { it.name }
				if (requiredProps.isNotEmpty()) {
					builder.putJsonArray("required") { requiredProps.forEach { add(it) } }
				}
			}
			
			is ToolMeta.Type.Enum -> {
				builder.put("type", "string")
				builder.put("enum", buildJsonArray { values.forEach { add(it) } })
			}
		}
	}
}
