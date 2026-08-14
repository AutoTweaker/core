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

import java.util.*

/**
 * 用于追踪消息何时被 agent 消费。
 */
interface Delivery {
	/**
	 * 消息是否仍未被消费。
	 */
	val isActive: Boolean
	
	/**
	 * 等待消息被消费，并得到消息的 id。
	 *
	 * @return 如果消息为空而被丢弃，返回 null。
	 * @throws kotlin.coroutines.cancellation.CancellationException 消息被取消
	 */
	suspend fun await(): UUID?
	
	/**
	 * 如果消息仍未被消费，取消消息处理。
	 *
	 * 不保证消息一定被取消，[isActive] 为 false 时可通过 [await] 查询状态。
	 */
	suspend fun cancel()
}
