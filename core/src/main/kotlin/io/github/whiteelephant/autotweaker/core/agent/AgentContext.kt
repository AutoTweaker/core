package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.Base64

data class AgentContext(
    val systemPrompt: String,
    val historyRounds: List<CompletedRound>,
    val currentRound: CurrentRound?,
) {
    sealed class Message {
        data class User(
            val content: String,
            val images: List<Base64>? = null,
            val timestamp: Long,
        ) : Message()

        data class Assistant(
            val reasoning: String? = null,
            val content: String? = null,
            val model: String,
            val timestamp: Long,
        ) : Message()

        data class Tool(
            val name: String,
            val call: Call,
            val callId: String,
            val result: Result,
        ) : Message() {
            data class Call(
                val arguments: String,
                val timestamp: Long,
            )

            data class Result(
                val content: String,
                val timestamp: Long,
            )
        }
    }

    data class CompletedRound(
        val userMessage: Message.User,
        val turns: List<Turn>?,
        val finalAssistantMessage: Message.Assistant?,
    )

    data class CurrentRound(
        val userMessage: Message.User,
        val turns: List<Turn>?,
        val assistantMessage: Message.Assistant? = null,
        val pendingToolCalls: List<PendingToolCall>? = null,
    ) {
        data class PendingToolCall(
            val callId: String,
            val name: String,
            val arguments: String,
            val timestamp: Long,
        )
    }

    data class Turn(
        val assistantMessage: Message.Assistant?,
        val tools: List<Message.Tool>,
    )
}
