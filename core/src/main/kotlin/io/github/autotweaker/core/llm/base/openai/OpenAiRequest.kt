package io.github.autotweaker.core.llm.base.openai

import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatRequest.Tool.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

abstract class OpenAiRequest {
	abstract val model: String?
	abstract val thinking: Thinking?
	abstract val frequencyPenalty: Double?
	abstract val presencePenalty: Double?
	abstract val responseFormat: ChatRequest.ResponseFormat?
	abstract val stop: List<String>?
	abstract val stream: Boolean?
	abstract val temperature: Double?
	abstract val topP: Double?
	abstract val maxCompletionTokens: Int?
	abstract val tools: List<Tool>?
	
	@Serializable
	data class Thinking(
		val type: Type
	) {
		@Serializable
		enum class Type {
			@SerialName("enabled")
			ENABLED,
			
			@SerialName("disabled")
			DISABLED
		}
	}
	
	@Serializable
	data class Tool(
		val type: String = "function",
		val function: Function
	) {
		@Serializable
		data class Function(
			val name: String,
			val description: String?,
			val parameters: Parameters,
			val strict: Boolean? = null
		)
	}
}
