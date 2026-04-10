package io.github.whiteelephant.autotweaker.core.llm.base.openai

abstract class OpenAiResponse {
    abstract val id: String?
    abstract val created: Long?
    abstract val model: String?
}
