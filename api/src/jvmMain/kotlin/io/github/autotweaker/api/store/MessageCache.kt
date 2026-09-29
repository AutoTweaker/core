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

package io.github.autotweaker.api.store

import io.github.autotweaker.api.types.message.AgentMessage
import java.util.*

/**
 * [AgentMessage] 的内存缓存，缺失会自动从硬盘加载，硬盘也没有会返回 null。
 */
interface MessageCache {
	/**
	 * 根据 [UUID] 获取一条 [AgentMessage]。
	 */
	fun get(id: UUID): AgentMessage?
}
