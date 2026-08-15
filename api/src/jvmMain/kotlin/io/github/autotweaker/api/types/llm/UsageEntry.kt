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

package io.github.autotweaker.api.types.llm

import java.util.*
import kotlin.time.Instant

/**
 * 表示 Usage 数据库中的一条记录。
 *
 * @param id 产生这条 Usage 的消息 id，除非 Usage 不属于任何会话中的消息。
 * @param modelId 产生这条 Usage 的模型 id，模型可能已被删除。
 * @param timestamp 产生这条 Usage 的时间戳。
 */
data class UsageEntry(
	val id: UUID = UUID.randomUUID(),
	val modelId: UUID,
	val timestamp: Instant,
	val usage: Usage,
)
