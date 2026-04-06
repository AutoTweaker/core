package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

abstract class OpenAiStreamChunk {
    abstract val id: String?
    abstract val created: Long?
    abstract val model: String?
}
