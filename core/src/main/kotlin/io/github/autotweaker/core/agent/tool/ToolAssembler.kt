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
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.Tool
import kotlinx.serialization.json.*

object ToolAssembler {
	fun assemble(tools: List<Tool>, settings: List<SettingItem>): List<ChatRequest.Tool>? {
		if (tools.isEmpty()) return null
		
		val reasonDescription: String = settings.find("core.agent.tool.description.reason")
		val metas = tools.map { it.resolveMeta(settings) }
		
		return metas.flatMap { meta ->
			meta.functions.map { func ->
				ChatRequest.Tool(
					name = "${meta.name}_${func.name}",
					description = func.description,
					parameters = func.parameters.toChatRequestParameters(reasonDescription),
				)
			}
		}
	}
	
	private fun Map<String, Tool.Function.Property>.toChatRequestParameters(
		reasonDescription: String
	): JsonElement = buildJsonObject {
		put("type", "object")
		putJsonObject("properties") {
			forEach { (name, prop) ->
				put(name, prop.toPropertyJson())
			}
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
	
	private fun Tool.Function.Property.toPropertyJson(): JsonElement = buildJsonObject {
		put("description", description)
		valueType.fillJsonObject(this)
	}
	
	private fun Tool.Function.Property.ValueType.fillJsonObject(builder: JsonObjectBuilder) {
		when (this) {
			is Tool.Function.Property.ValueType.StringValue -> {
				builder.put("type", "string")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is Tool.Function.Property.ValueType.NumberValue -> {
				builder.put("type", "number")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is Tool.Function.Property.ValueType.IntegerValue -> {
				builder.put("type", "integer")
				enum?.let { builder.put("enum", buildJsonArray { it.forEach { e -> add(e) } }) }
			}
			
			is Tool.Function.Property.ValueType.BooleanValue -> {
				builder.put("type", "boolean")
			}
			
			is Tool.Function.Property.ValueType.ArrayValue -> {
				builder.put("type", "array")
				builder.put("items", buildJsonObject { items.fillJsonObject(this) })
			}
			
			is Tool.Function.Property.ValueType.ObjectValue -> {
				builder.put("type", "object")
				builder.putJsonObject("properties") {
					properties.forEach { (name, vt) ->
						put(name, buildJsonObject { vt.fillJsonObject(this) })
					}
				}
			}
		}
	}
}
