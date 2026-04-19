package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MiMoRequest(
	val messages: List<MiMoMessage>,
	@SerialName("tool_choice")
	val toolChoice: String? = null,
	
	override val model: String,
	override val thinking: Thinking? = null,
	@SerialName("max_completion_tokens")
	override val maxCompletionTokens: Int? = null,
	@SerialName("frequency_penalty")
	override val frequencyPenalty: Double? = null,
	@SerialName("presence_penalty")
	override val presencePenalty: Double? = null,
	@SerialName("response_format")
	override val responseFormat: ChatRequest.ResponseFormat? = null,
	override val stop: List<String>? = null,
	override val stream: Boolean? = null,
	override val temperature: Double? = null,
	@SerialName("top_p")
	override val topP: Double? = null,
	override val tools: List<Tool>? = null
) : OpenAiRequest()
