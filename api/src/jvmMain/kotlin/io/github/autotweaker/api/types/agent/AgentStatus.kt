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

/**
 * 表示 Agent 当前状态。
 */
enum class AgentStatus {
	/**
	 * Agent 主循环空闲，但上下文压缩可能正在进行。
	 */
	FREE,
	
	/**
	 * Agent 主循环发生不可恢复错误，已经无法工作。
	 */
	FAILED,
	
	/**
	 * Agent 正在调用 LLM，在此期间不会有上下文更新。
	 */
	THINKING,
	
	/**
	 * Agent 正在执行工具，在此期间不会有上下文更新。
	 */
	TOOL_CALLING,
	
	/**
	 * 短暂的瞬时状态，Agent 正在执行内部运算，如更新上下文或校验工具调用。
	 */
	PROCESSING,
	
	/**
	 * Agent 正在等待工具审批。
	 */
	WAITING,
}
