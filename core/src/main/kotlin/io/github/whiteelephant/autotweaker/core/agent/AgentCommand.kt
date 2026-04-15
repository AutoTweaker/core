package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.Base64
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.llm.ChatResult

sealed class AgentCommand {
    data class SendMessage(
        val content: String,
        val images: List<Base64>? = null,
    ) : AgentCommand()

    data class ApproveToolCall(
        val approvals: List<Approve>,
    ) : AgentCommand() {
        data class Approve(
            val callId: String,
            val reason: String? = null,
            val approved: Boolean = true,
        )
    }

    data class UpdateModel(
        val model: Model,
        val fallbackModels: List<Model>? = null,
        val thinking: Boolean? = null,
    ) : AgentCommand()

    object Pause : AgentCommand()
    object Resume : AgentCommand()
    object Cancel : AgentCommand()
    object Stop : AgentCommand()
    object Retry : AgentCommand()
    object Compact : AgentCommand()
}
