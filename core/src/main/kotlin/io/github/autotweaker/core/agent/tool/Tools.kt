package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.Tool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class Tools(settings: List<SettingItem>) {
	private val _settings = settings
	private val enableDescription: String = settings.find("core.agent.tool.description.enable")
	
	data class Entry(
		val tool: Tool<*, *>,
		val active: Boolean = false,
	)
	
	private val _entries = mutableListOf<Entry>()
	
	@Suppress("unused")
	val entries: List<Entry> get() = _entries
	
	fun add(tool: Tool<*, *>) {
		_entries.add(Entry(tool))
	}
	
	fun assembleTools(): List<ChatRequest.Tool>? {
		val activeTools = _entries.filter { it.active }.map { it.tool }
		val active = ToolAssembler.assemble(activeTools, _settings)
		
		val inactive = _entries
			.filter { !it.active }
			.map { it.tool }
			.takeIf { it.isNotEmpty() }
			?.map { tool ->
				ChatRequest.Tool(
					name = tool.name,
					description = tool.description,
					parameters = buildJsonObject {
						put("type", "object")
						put("properties", buildJsonObject {
							put("enable", buildJsonObject {
								put("type", "boolean")
								put("description", enableDescription)
							})
						})
					},
				)
			}
		
		return if (active != null || inactive != null) {
			(active ?: emptyList()) + (inactive ?: emptyList())
		} else {
			null
		}
	}
}