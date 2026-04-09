package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.*
import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import io.ktor.client.*
import kotlinx.serialization.serializer
import io.ktor.util.reflect.typeInfo

class DeepSeekClient(
    apiKey: String,
    httpClient: HttpClient,
    baseUrl: String = "https://api.deepseek.com/v1"
) : AbstractOpenAiClient<DeepSeekRequest, DeepSeekResponse, DeepSeekStreamChunk>(
    apiKey = apiKey,
    baseUrl = baseUrl,
    httpClient = httpClient,
    requestTypeInfo = typeInfo<DeepSeekRequest>(),
    responseTypeInfo = typeInfo<DeepSeekResponse>(),
    chunkSerializer = serializer<DeepSeekStreamChunk>(),
) {
    override fun createRequestBody(request: ChatRequest): DeepSeekRequest {
        val mappedMessages = request.messages.mapNotNull { msg ->
            when (msg) {
                is ChatMessage.SystemMessage -> DeepSeekMessage.SystemMessage(
                    content = msg.content
                )

                is ChatMessage.UserMessage -> DeepSeekMessage.UserMessage(
                    content = msg.content
                )

                is ChatMessage.AssistantMessage -> DeepSeekMessage.AssistantMessage(
                    content = msg.content,
                    reasoningContent = msg.reasoningContent,
                    toolCalls = msg.toolCalls?.map { tc ->
                        DeepSeekMessage.AssistantMessage.ToolCall(
                            id = tc.id,
                            function = DeepSeekMessage.AssistantMessage.ToolCall.Function(
                                name = tc.name,
                                arguments = tc.arguments
                            )
                        )
                    }
                )

                is ChatMessage.ToolMessage -> DeepSeekMessage.ToolMessage(
                    content = msg.content,
                    toolCallId = msg.toolCallId
                )

                is ChatMessage.ErrorMessage -> null
            }
        }

        val isThinkingEnabled = request.thinking == true

        return DeepSeekRequest(
            model = request.model,
            messages = mappedMessages,
            stream = request.stream,
            tools = request.tools?.map { tool ->
                OpenAiRequest.Tool(
                    function = OpenAiRequest.Tool.Function(
                        name = tool.name,
                        description = tool.description,
                        parameters = tool.parameters
                    )
                )
            },
            thinking = if (isThinkingEnabled) OpenAiRequest.Thinking(OpenAiRequest.Thinking.Type.ENABLED) else null,
            temperature = request.temperature,
            maxCompletionTokens = request.maxTokens,
            topP = request.topP,
            frequencyPenalty = request.frequencyPenalty,
            presencePenalty = request.presencePenalty
        )
    }

    override fun mapToChatResult(response: DeepSeekResponse): ChatResult {
        val choice = response.choices.firstOrNull()
        val msg = choice?.message

        return ChatResult(
            message = ChatMessage.AssistantMessage(
                content = msg?.content,
                reasoningContent = msg?.reasoningContent,
                toolCalls = msg?.toolCalls?.map { tc ->
                    ChatMessage.AssistantMessage.ToolCall(
                        id = tc.id,
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                },
                createdAt = response.created,
                model = response.model
            ),
            usage = response.usage.let { u ->
                Usage(
                    totalTokens = u.totalTokens,
                    promptTokens = u.promptTokens,
                    completionTokens = u.completionTokens,
                    reasoningTokens = u.completionTokensDetails?.reasoningTokens,
                    cacheHitTokens = u.promptCacheHitTokens,
                    cacheMissTokens = u.promptCacheMissTokens
                )
            },
            finishReason = choice?.finishReason
        )
    }

    override fun mapChunkToChatResult(chunk: DeepSeekStreamChunk): ChatResult {
        val choice = chunk.choices.firstOrNull()
        val delta = choice?.delta

        return ChatResult(
            message = ChatMessage.AssistantMessage(
                content = delta?.content,
                reasoningContent = delta?.reasoningContent,
                createdAt = System.currentTimeMillis() / 1000,
                model = chunk.model
            ),
            usage = chunk.usage?.let { u ->
                Usage(
                    totalTokens = u.totalTokens,
                    promptTokens = u.promptTokens,
                    completionTokens = u.completionTokens,
                    reasoningTokens = u.completionTokensDetails?.reasoningTokens,
                    cacheHitTokens = u.promptCacheHitTokens,
                    cacheMissTokens = u.promptCacheMissTokens
                )
            },
            finishReason = choice?.finishReason
        )
    }

    override fun extractToolCalls(chunk: DeepSeekStreamChunk): List<ToolCallFragment>? {
        return chunk.choices.firstOrNull()?.delta?.toolCalls?.map { tc ->
            ToolCallFragment(
                index = tc.index,
                id = tc.id,
                name = tc.function?.name,
                arguments = tc.function?.arguments
            )
        }
    }
}
