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
 * 表示一个未完成的工具调用的当前状态，这些工具调用的相关状态只存在于内存中。
 */
enum class ToolCallStatus {
	/**
	 * 正在等待用户审批。
	 */
	PENDING,
	
	/**
	 * 已通过审批，正在等待执行。
	 *
	 * 工具调用是串行的，所以需要排队。
	 */
	WAITING,
	
	/**
	 * 正在调用工具。
	 */
	CALLING,
	
	/**
	 * 工具调用已经完成（或者解析失败未执行），等待合入一个新的 Turn。
	 */
	FINISHED
}
