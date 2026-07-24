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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.ToolMeta.Prop
import kotlinx.serialization.json.*

object ToolAssembler : Loggable, Settable {
	fun assemble(
		tools: MetaCache,
		active: (String) -> Boolean
	): List<ChatRequest.Tool>? {
		if (tools.isEmpty()) return null
		
		log.debug("Started tool assembly  toolCount={}", tools.size)
		
		val reasonDescription = setting(AgentToolSettings.ReasonEmptyError())
		val enableDesc = setting(AgentToolSettings.EnableDescription())
		
		return tools.flatMap {
			val meta = it.value.first
			if (active(it.key)) meta.functions.map { func ->
				ChatRequest.Tool(
					name = "${it.key}-${func.name}",
					description = func.description,
					parameters = func.parameters.toJsonSchema(reasonDescription),
				)
			} else listOf(
				ChatRequest.Tool(
					name = meta.name,
					description = meta.description,
					parameters = inactiveParameters(enableDesc),
				)
			)
		}
	}
	
	private fun inactiveParameters(enableDesc: String) = buildJsonObject {
		put("type", "object")
		putJsonObject("properties") {
			put("enable", buildJsonObject {
				put("type", "boolean")
				put("description", enableDesc)
			})
		}
	}
	
	private fun List<Prop>.toJsonSchema(
		reasonDescription: String
	): JsonElement = buildJsonObject {
		put("type", "object")
		putJsonObject("properties") {
			put("reason", buildJsonObject {
				put("type", "string")
				put("description", reasonDescription)
			})
			putProperties(this@toJsonSchema)
		}
		putJsonArray("required") {
			add("reason")
			addRequired(this@toJsonSchema)
		}
	}
	
	private fun JsonObjectBuilder.putProp(type: ToolMeta.Type, description: String) {
		putType(type)
		put("description", description)
	}
	
	private fun JsonObjectBuilder.putType(type: ToolMeta.Type) {
		when (type) {
			is ToolMeta.Type.TString -> put("type", "string")
			is ToolMeta.Type.TInt, is ToolMeta.Type.TLong -> put("type", "integer")
			is ToolMeta.Type.TDouble -> put("type", "number")
			is ToolMeta.Type.TBoolean -> put("type", "boolean")
			
			is ToolMeta.Type.TList -> {
				put("type", "array")
				put("items", buildJsonObject {
					putType(type.element)
				})
			}
			
			is ToolMeta.Type.TMap -> {
				put("type", "object")
				put("additionalProperties", buildJsonObject {
					putType(type.element)
				})
			}
			
			is ToolMeta.Type.OneOf -> {
				put("type", "object")
				putJsonArray("oneOf") {
					type.variants.forEach { variant ->
						add(buildJsonObject {
							put("type", "object")
							putJsonObject("properties") {
								put("type", buildJsonObject {//不是type，是名为type的字段，给反序列化用
									put("const", variant.name)
									put("description", variant.description)
								})
								putProperties(variant.properties)
							}
							putJsonArray("required") {
								add("type")
								addRequired(variant.properties)
							}
						})
					}
				}
			}
			
			is ToolMeta.Type.Obj -> {
				put("type", "object")
				putJsonObject("properties") {
					putProperties(type.properties)
				}
				putJsonArray("required") {
					addRequired(type.properties)
				}
			}
			
			is ToolMeta.Type.Enum -> {
				put("type", "string")
				put("enum", buildJsonArray {
					type.values.forEach { add(it) }
				})
			}
		}
	}
	
	private fun JsonObjectBuilder.putProperties(properties: List<Prop>) = properties.forEach {
		put(it.name, buildJsonObject {
			putProp(it.type, it.description)
		})
	}
	
	private fun JsonArrayBuilder.addRequired(properties: List<Prop>) = properties.forEach {
		if (it.required) add(it.name)
	}
}
