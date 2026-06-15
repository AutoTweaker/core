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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.core.domain.model.Model

sealed class AgentCommand {
	data object Stop : AgentCommand()
	
	// 当前任务完成后暂停，并令Agent空闲
	data object Pause : AgentCommand()
	
	// 取消当前工具调用
	data object CancelTool : AgentCommand()
	
	// 取消当前压缩
	data object CancelCompact : AgentCommand()
	
	// 压缩对话
	data object Compact : AgentCommand()
	
	// 更新模型
	data class UpdateModel(
		val model: Model,
		val fallbackModels: List<Model>? = null,
		val summarizeModel: Model? = null,
		val thinking: Boolean? = null,
	) : AgentCommand()
	
	// 批准单个工具调用
	data class ApproveTool(val approval: ToolApprove) : AgentCommand()
}
