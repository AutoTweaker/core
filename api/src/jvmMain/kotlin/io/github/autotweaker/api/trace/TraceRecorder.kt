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

import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.UpperSnakeCase

/**
 * 记录数据到 `Traces` 数据库，通常用于记录有调试价值的原始数据，不要当日志 API 用。
 *
 * AutoTweaker 会自动清理旧的数据，但并非实时监控，避免每分钟几个 GB 几万条这么存。
 *
 * @see io.github.autotweaker.api.Traceable
 */
interface TraceRecorder {
	/**
	 * 添加一条数据记录，AutoTweaker 会根据存入方、命名空间、时间戳来共同区分条目。
	 *
	 * @param namespace 命名空间，用于分类，必须符合 `kebab-case`，请不要一条数据一个命名空间。
	 */
	fun add(namespace: KebabCase, content: Any)
	
	/**
	 * 此重载适用于存储多字段的数据，格式如下：
	 *
	 * ```
	 * NAME=autotweaker
	 * MESSAGE=Message(value=hello)
	 * ```
	 *
	 * 关于 [add] 的更多信息请见另一重载（`fun add(namespace: KebabCase, content: Any)`）
	 */
	fun add(namespace: KebabCase, content: Map<UpperSnakeCase, Any>)
	
	/**
	 * 记录一条异常的完整堆栈到命名空间 `e`，通常使用 [catching] 来自动处理。
	 *
	 * @param e 异常对象
	 */
	fun exception(e: Throwable) = add("e".toKebab(), e.stackTraceToString())
}
