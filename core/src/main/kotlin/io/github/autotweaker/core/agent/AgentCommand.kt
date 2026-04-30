package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model

@Suppress("unused")
sealed class AgentCommand {
	sealed class Directive : AgentCommand() {
		//终止Agent当前任何任务并令Agent空闲
		data object Stop : Directive()
		
		//在当前任务完成后暂停
		data object Pause : Directive()
		
		//从暂停中恢复
		data object Resume : Directive()
		
		//取消工具调用或上下文压缩并继续
		data object Cancel : Directive()
		
		//重试（当出错时）
		data object Retry : Directive()
		
		//压缩对话
		data object Compact : Directive()
		
		//更新模型
		data class UpdateModel(
			val model: Model,
			val fallbackModels: List<Model>? = null,
			val thinking: Boolean? = null,
		) : Directive()
	}
	
	sealed class Message : AgentCommand() {
		data class SendMessage(
			val content: String,
			val images: List<Base64>? = null,
		) : Message()
		
		data class ApproveToolCall(
			val approvals: List<Approve>,
		) : Message() {
			data class Approve(
				val callId: String,
				val reason: String? = null,
				val approved: Boolean = true,
			)
		}
	}
}
