package io.github.autotweaker.core.tool.impl.read

interface ToolCallHistory {
	data class Entry(
		val name: String,
		val arguments: String,
		val resultContent: String,
	)
	
	fun getAll(): List<Entry>
}