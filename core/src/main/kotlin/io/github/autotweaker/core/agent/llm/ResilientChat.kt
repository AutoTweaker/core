package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.LlmClientLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_MAX_RETRIES = 3
private val RETRY_BASE_DELAY = 1.seconds

data class ResilientChatResult(
	val result: ChatResult,
	val retrying: Model?,
)

fun resilientChat(
	model: Model,
	fallbackModels: List<Model>?,
	request: ChatRequest,
	maxRetries: Int = DEFAULT_MAX_RETRIES,
): Flow<ResilientChatResult> = flow {
	require(maxRetries > 0) { "maxRetries must be positive" }

	var candidates = buildList {
		add(model)
		addAll(fallbackModels.orEmpty())
	}
	
	// 图像兼容性预处理：存在支持图像的模型时，屏蔽所有不支持的
	if (request.messages.any { it is ChatMessage.UserMessage && !it.pictures.isNullOrEmpty() }) {
		candidates = candidates.filter { it.modelInfo.supportsImage }.ifEmpty { candidates }
	}
	
	while (candidates.isNotEmpty()) {
		val current = candidates.first()
		val rules = current.provider.errorHandlingRules
		
		for (retryAttempt in 0 until maxRetries) {
			val chatRequest = request.adapt(current)
			val client = LlmClientLoader.load(current.provider.name)
			val results = client.chat(
				request = chatRequest,
				apiKey = current.provider.apiKey,
				baseUrl = current.provider.baseUrl,
			)
			
			var lastError: ChatResult? = null
			var lastStatusCode: Int? = null
			
			results.collect { result ->
				if (result.message is ChatMessage.ErrorMessage) {
					lastError = result
					lastStatusCode = result.message.statusCode?.value
				} else {
					emit(ResilientChatResult(result, retrying = null))
				}
			}
			
			val error = lastError ?: return@flow
			
			// 匹配错误处理规则
			val matchedRule = if (lastStatusCode != null) {
				rules.find { it.statusCode == lastStatusCode }
			} else {
				null
			}
			
			when (matchedRule?.strategy) {
				RecoveryStrategy.RETRY -> {
					if (retryAttempt < maxRetries - 1) {
						emit(ResilientChatResult(error, retrying = current))
						delay(RETRY_BASE_DELAY * (1 shl retryAttempt))
						continue
					}
					// 重试耗尽，屏蔽当前模型
					candidates = candidates.drop(1)
				}
				
				RecoveryStrategy.CONTEXT_FALLBACK -> {
					// 屏蔽上下文窗口 <= 当前模型的候选模型
					candidates = candidates.filter { it.modelInfo.contextWindow > current.modelInfo.contextWindow }
				}
				
				RecoveryStrategy.PROVIDER_FALLBACK -> {
					// 屏蔽与当前模型同 provider 的候选模型
					candidates = candidates.filter { it.provider.name != current.provider.name }
				}
				
				RecoveryStrategy.FALLBACK, null -> {
					// 屏蔽当前模型
					candidates = candidates.drop(1)
				}
			}
			
			emit(ResilientChatResult(error, retrying = candidates.firstOrNull()))
			
			break // 跳出当前模型的重试循环，回到外层取下一个候选
		}
	}
	
	throw IllegalStateException("All candidate models exhausted without success")
}

private fun ChatRequest.adapt(model: Model): ChatRequest {
	val lastUserMessageIndex = messages.indexOfLast { it is ChatMessage.UserMessage }
	val stripPictures = !model.modelInfo.supportsImage &&
			messages.any { it is ChatMessage.UserMessage && !it.pictures.isNullOrEmpty() }
	val stripThinking = !model.modelInfo.supportsReasoning && thinking == true
	
	return copy(
		model = model.name,
		thinking = if (stripThinking) null else thinking,
		temperature = model.config?.temperature,
		maxTokens = model.config?.maxTokens,
		messages = messages.mapIndexed { index, msg ->
			var result = msg
			if (stripPictures && result is ChatMessage.UserMessage) {
				result = result.copy(pictures = null)
			}
			if (result is ChatMessage.AssistantMessage && result.reasoningContent != null) {
				val stripReasoning = when {
					!model.modelInfo.supportsReasoning || thinking != true -> true
					index < lastUserMessageIndex -> true
					else -> false
				}
				if (stripReasoning) {
					result = result.copy(reasoningContent = null)
				}
			}
			result
		},
	)
}
