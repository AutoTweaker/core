package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.llm.*
import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import io.ktor.client.*
import kotlinx.serialization.serializer
import io.ktor.util.reflect.typeInfo

class MiMoClient(
    apiKey: String,
    httpClient: HttpClient,
    baseUrl: Url = Url("https://api.xiaomimimo.com/v1")
) : AbstractOpenAiClient<MiMoRequest, MiMoResponse, MiMoStreamChunk>(
    apiKey = apiKey,
    baseUrl = baseUrl,
    httpClient = httpClient,
    requestTypeInfo = typeInfo<MiMoRequest>(),
    responseTypeInfo = typeInfo<MiMoResponse>(),
    chunkSerializer = serializer<MiMoStreamChunk>(),
) {
    override fun createRequestBody(request: ChatRequest): MiMoRequest {
        val mappedMessages = request.messages.mapNotNull { msg ->
            when (msg) {
                is ChatMessage.SystemMessage -> MiMoMessage.DeveloperMessage(
                    content = listOf(MiMoMessage.Content.TextPart(text = msg.content))
                )

                is ChatMessage.UserMessage -> MiMoMessage.UserMessage(
                    content = listOf(MiMoMessage.Content.TextPart(text = msg.content)) + msg.pictures.orEmpty()
                        .map { base64 ->
                            MiMoMessage.Content.ImagePart(
                                imageUrl = MiMoMessage.Content.ImagePart.Url(base64.value)
                            )
                        }
                )

                is ChatMessage.AssistantMessage -> MiMoMessage.AssistantMessage(
                    content = if (msg.content != null) listOf(MiMoMessage.Content.TextPart(text = msg.content)) else null,
                    reasoningContent = msg.reasoningContent,
                    toolCalls = msg.toolCalls?.map { tc ->
                        MiMoToolCall(
                            id = tc.id,
                            function = MiMoToolCall.Function(
                                name = tc.name,
                                arguments = tc.arguments
                            )
                        )
                    }
                )

                is ChatMessage.ToolMessage -> MiMoMessage.ToolMessage(
                    content = listOf(MiMoMessage.Content.TextPart(text = msg.content)),
                    toolCallId = msg.toolCallId
                )

                is ChatMessage.ErrorMessage -> null
            }
        }

        return MiMoRequest(
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
            thinking = if (request.thinking == true) OpenAiRequest.Thinking(OpenAiRequest.Thinking.Type.ENABLED) else null,
            temperature = request.temperature,
            maxCompletionTokens = request.maxTokens,
            topP = request.topP,
            frequencyPenalty = request.frequencyPenalty,
            presencePenalty = request.presencePenalty
        )
    }

    override fun mapToChatResult(response: MiMoResponse): ChatResult {
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
                    cacheHitTokens = u.promptTokensDetails?.cachedTokens,
                    cacheMissTokens = if (u.promptTokensDetails?.cachedTokens != null) u.promptTokens - u.promptTokensDetails.cachedTokens else null,
                    imageTokens = u.promptTokensDetails?.imageTokens
                )
            },
            finishReason = choice?.finishReason?.toFinishReason()
        )
    }

    override fun mapChunkToChatResult(chunk: MiMoStreamChunk): ChatResult {
        val choice = chunk.choices.firstOrNull()
        val delta = choice?.delta

        return ChatResult(
            message = ChatMessage.AssistantMessage(
                content = delta?.content,
                reasoningContent = delta?.reasoningContent,
                createdAt = chunk.created,
                model = chunk.model
            ),
            usage = chunk.usage?.let { u ->
                Usage(
                    totalTokens = u.totalTokens,
                    promptTokens = u.promptTokens,
                    completionTokens = u.completionTokens,
                    reasoningTokens = u.completionTokensDetails?.reasoningTokens,
                    cacheHitTokens = u.promptTokensDetails?.cachedTokens,
                    cacheMissTokens = if (u.promptTokensDetails?.cachedTokens != null) u.promptTokens - u.promptTokensDetails.cachedTokens else null,
                    imageTokens = u.promptTokensDetails?.imageTokens
                )
            },
            finishReason = choice?.finishReason?.toFinishReason()
        )
    }

    private fun MiMoFinishReason.toFinishReason() = ChatResult.FinishReason(
        reason = value,
        type = when (this) {
            MiMoFinishReason.STOP -> ChatResult.FinishReason.Type.STOP
            MiMoFinishReason.TOOL_CALLS -> ChatResult.FinishReason.Type.TOOL
            MiMoFinishReason.CONTENT_FILTER -> ChatResult.FinishReason.Type.FILTER
            MiMoFinishReason.LENGTH -> ChatResult.FinishReason.Type.LENGTH
            MiMoFinishReason.REPETITION_TRUNCATION -> ChatResult.FinishReason.Type.ERROR
        }
    )

    override fun extractToolCalls(chunk: MiMoStreamChunk): List<ToolCallFragment>? {
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
