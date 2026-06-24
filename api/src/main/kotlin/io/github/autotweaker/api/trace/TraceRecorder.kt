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

package io.github.autotweaker.api.trace

/**
 * 记录数据到 `Traces` 数据库，通常用于记录有调试价值的原始数据，不要当日志 API 用。
 *
 * AutoTweaker 会自动清理旧的数据，但并非实时监控，避免每分钟几个 GB 几万条这么存。
 */
interface TraceRecorder {
	/**
	 * 添加一条数据记录，[content] 最好不要是像日志一样的自然语言，直接将原始数据 [toString] 即可。
	 *
	 * AutoTweaker 会根据存入方、命名空间、时间戳来共同区分条目。
	 */
	fun add(namespace: String, content: String)
	
	/**
	 * 记录一条异常的完整堆栈到命名空间 `e`，通常使用 [catching] 来自动处理。
	 */
	fun exception(e: Throwable) = add("e", e.stackTraceToString())
}
