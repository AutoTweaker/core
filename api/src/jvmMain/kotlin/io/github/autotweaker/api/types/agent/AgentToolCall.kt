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

package io.github.autotweaker.api.types.agent

import io.github.autotweaker.api.types.message.AgentMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * 表示一条正在处理的工具调用，可能有这些状态：[ToolCallStatus]。
 *
 * 这些工具调用的相关状态只存在于内存中。
 *
 * [call] 的 id 可以用来区分一个 [AgentToolCall]。
 *
 * @property call 工具调用的请求消息。
 * @property result 工具调用的响应消息，待 [status] 为 [ToolCallStatus.FINISHED] 后将可取到非空值。
 * @property status 工具调用的当前状态。
 */
interface AgentToolCall {
	val call: AgentMessage.Tool.Call
	val result: AgentMessage.Tool.Result?
	val status: StateFlow<ToolCallStatus>
}
