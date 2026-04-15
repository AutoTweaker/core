package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.data.json.model.Provider.ErrorHandlingRule
import io.github.whiteelephant.autotweaker.core.data.json.model.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
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

/**
 * 带重试与 fallback 策略的 [forwardChat] 封装。
 *
 * 返回一个连续的 [Flow]，每次尝试的结果都会被 emit（包括失败的 [ChatResult]），
 * 调用方可实时观察每次尝试。当某次尝试成功（不含 [ChatMessage.ErrorMessage]）或
 * 所有候选模型耗尽时，Flow 结束。
 *
 * 策略匹配规则（基于 [ErrorHandlingRule]）：
 * - [RecoveryStrategy.RETRY]：指数退避重试当前模型，重试耗尽后屏蔽当前模型
 * - [RecoveryStrategy.FALLBACK]：屏蔽当前模型，从剩余候选头部取下一个
 * - [RecoveryStrategy.CONTEXT_FALLBACK]：
 *   屏蔽上下文窗口 <= 当前模型的候选模型，从剩余头部取下一个
 * - [RecoveryStrategy.PROVIDER_FALLBACK]：
 *   屏蔽与当前模型同 provider 的候选模型，从剩余头部取下一个
 * - 无匹配规则：视为 FALLBACK
 *
 * 图像兼容性处理：若请求消息包含 pictures，当前模型不支持图像时，
 * 若候选列表中存在支持图像的模型则屏蔽所有不支持的模型；
 * 若不存在则在请求中剔除 pictures 字段继续发送。
 *
 * @param model           主模型
 * @param fallbackModels  备选模型列表（按优先级排列）
 * @param request         原始请求
 * @param maxRetries      每个模型的最大重试次数（默认 [DEFAULT_MAX_RETRIES]）
 * @throws IllegalStateException 候选模型全部耗尽仍失败
 */
fun resilientChat(
    model: Model,
    fallbackModels: List<Model>?,
    request: ChatRequest,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
): Flow<ResilientChatResult> = flow {
    var candidates = buildList {
        add(model)
        addAll(fallbackModels.orEmpty())
    }

    // 图像兼容性预处理：检查请求是否包含 pictures
    val hasPictures = request.messages.any { msg ->
        msg is ChatMessage.UserMessage && !msg.pictures.isNullOrEmpty()
    }
    var stripPictures = false

    if (hasPictures) {
        val hasImageModel = candidates.any { it.supportsImage }
        if (hasImageModel) {
            // 存在支持图像的模型，屏蔽所有不支持的
            candidates = candidates.filter { it.supportsImage }
        } else {
            // 不存在支持图像的模型，标记剔除 pictures
            stripPictures = true
        }
    }

    while (candidates.isNotEmpty()) {
        val current = candidates.first()
        val rules = current.provider.errorHandlingRules

        for (retryAttempt in 0 until maxRetries) {
            val chatRequest = request.copy(
                model = current.name,
                messages = if (stripPictures) {
                    request.messages.map { msg ->
                        if (msg is ChatMessage.UserMessage) msg.copy(pictures = null) else msg
                    }
                } else {
                    request.messages
                },
            )
            val results = forwardChat(
                provider = current.provider.name,
                apiKey = current.provider.apiKey,
                baseUrl = current.provider.baseUrl,
                request = chatRequest,
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
                    candidates = candidates.filter { it.contextWindow > current.contextWindow }
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
