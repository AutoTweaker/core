package io.github.autotweaker.core.tool.impl.read

interface SummarizeService {
	suspend fun summarize(content: String, prompt: String): String
}