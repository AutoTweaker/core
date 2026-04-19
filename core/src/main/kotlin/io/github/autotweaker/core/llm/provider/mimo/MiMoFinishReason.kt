package io.github.autotweaker.core.llm.provider.mimo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MiMoFinishReason(val value: String) {
	@SerialName("stop")
	STOP("stop"),
	
	@SerialName("length")
	LENGTH("length"),
	
	@SerialName("tool_calls")
	TOOL_CALLS("tool_calls"),
	
	@SerialName("content_filter")
	CONTENT_FILTER("content_filter"),
	
	@SerialName("repetition_truncation")
	REPETITION_TRUNCATION("repetition_truncation")
}
