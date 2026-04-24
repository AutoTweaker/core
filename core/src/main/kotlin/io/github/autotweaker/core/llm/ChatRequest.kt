package io.github.autotweaker.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

data class ChatRequest(
	val model: String,
	val messages: List<ChatMessage>,
	val thinking: Boolean? = null,
	val stream: Boolean = false,
	
	val maxTokens: Int? = null,
	val tools: List<Tool>? = null,
	val toolCallRequired: Boolean? = null,
	
	val temperature: Double? = null,
	val topP: Double? = null,
	val frequencyPenalty: Double? = null,
	val presencePenalty: Double? = null,
	val responseFormat: ResponseFormat? = null
) {
	data class Tool(
		val name: String,
		val description: String,
		val parameters: JsonElement
	)
	
	@Serializable
	data class ResponseFormat(
		val type: Type
	) {
		@Serializable
		@Suppress("unused")
		enum class Type {
			@SerialName("text")
			TEXT,
			
			@SerialName("json_object")
			JSON_OBJECT
		}
	}
}
