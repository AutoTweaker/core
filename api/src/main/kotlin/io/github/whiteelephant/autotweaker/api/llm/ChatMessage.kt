package io.github.whiteelephant.autotweaker.api.llm

import kotlinx.serialization.Serializable

/**
 * 表示聊天中的一条消息。
 * 在大型语言模型（LLM）的API调用中，通常需要传递一个消息列表。
 * 每条消息都有一个角色（如"user"、"assistant"、"system"）和内容。
 *
 * @property role 消息的角色，例如："user"、"assistant"、"system"
 * @property content 消息的文本内容
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    /**
     * 伴生对象提供了一些预定义的角色常量，方便使用。
     */
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}