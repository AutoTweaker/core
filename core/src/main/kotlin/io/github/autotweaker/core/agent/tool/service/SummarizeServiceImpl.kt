package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.resilientChat
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.tool.impl.read.SummarizeService
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock

@Suppress("unused")
class SummarizeServiceImpl(
	private val model: Model,
	private val fallbackModels: List<Model>? = null,
) : SummarizeService {
	override suspend fun summarize(content: String, prompt: String): String {
		val request = ChatRequest(
			model = model.name,
			messages = listOf(
				ChatMessage.SystemMessage(prompt, Clock.System.now()),
				ChatMessage.UserMessage(content, Clock.System.now()),
			),
			stream = false,
		)
		
		val results = resilientChat(model, fallbackModels, request).toList()
		val success = results.filter { it.retrying == null }.map { it.result }
		return success.firstNotNullOfOrNull { it.message?.content }
			?: throw IllegalStateException("No response from LLM")
	}
}
