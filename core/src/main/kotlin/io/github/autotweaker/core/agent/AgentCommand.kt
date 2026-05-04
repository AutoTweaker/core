/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import java.util.*

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
			val id: UUID = UUID.randomUUID(),
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
