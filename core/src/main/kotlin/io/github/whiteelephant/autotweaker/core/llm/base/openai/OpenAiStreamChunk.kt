package io.github.whiteelephant.autotweaker.core.llm.base.openai

import java.time.Instant

abstract class OpenAiStreamChunk {
    abstract val id: String?
    abstract val created: Instant?
    abstract val model: String?
}
