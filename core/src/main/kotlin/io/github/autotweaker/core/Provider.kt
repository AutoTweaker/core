package io.github.autotweaker.core

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
	val name: String,
	val providerType: String,
	val apiKey: String,
	val baseUrl: Url,
	val models: List<Model>,
	val errorHandlingRules: List<ErrorHandlingRule>
) {
	@Serializable
	data class ErrorHandlingRule(
		val statusCode: Int,
		val strategy: RecoveryStrategy
	) {
		@Serializable
		enum class RecoveryStrategy {
			RETRY,
			FALLBACK,
			CONTEXT_FALLBACK,
			PROVIDER_FALLBACK,
		}
	}
	
	@Serializable
	data class Model(
		val name: String,
		val modelInfo: ModelInfo,
		val config: Config? = null,
	) {
		@Serializable
		data class ModelInfo(
			val id: String,
			
			val contextWindow: Int,
			val maxOutputTokens: Int,
			val price: TokenPrice,
			
			val supportsStreaming: Boolean,
			val supportsToolCalls: Boolean,
			val supportsReasoning: Boolean,
			val supportsImage: Boolean,
			val supportsJsonOutput: Boolean,
		)
		
		@Serializable
		data class TokenPrice(
			val inputPrice: List<PriceTier>,
			val outputPrice: List<PriceTier>,
		) {
			@Serializable
			data class PriceTier(
				val fromTokens: Int,
				val toTokens: Int? = null,
				val price: Price,
				val cachedPrice: Price? = null
			)
		}
		
		@Serializable
		data class Config(
			val temperature: Double?,
			val maxTokens: Int?,
			val compactContextUsage: Double?,
			val compactTotalTokens: Double?,
		)
	}
}