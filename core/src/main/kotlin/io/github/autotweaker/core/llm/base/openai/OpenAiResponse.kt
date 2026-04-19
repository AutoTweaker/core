package io.github.autotweaker.core.llm.base.openai

import kotlin.time.Instant

abstract class OpenAiResponse {
	abstract val id: String?
	abstract val created: Instant?
	abstract val model: String?
}
