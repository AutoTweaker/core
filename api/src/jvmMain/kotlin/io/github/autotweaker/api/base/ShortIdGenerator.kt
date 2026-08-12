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

package io.github.autotweaker.api.base

import java.util.concurrent.atomic.AtomicLong

/**
 * 生成一个自增短 id，用于日志记录。
 *
 * 从 0 开始，程序重启后会归零。
 */
object ShortIdGenerator {
	private val counter = AtomicLong(0)
	
	/**
	 * 获取数字 id。
	 */
	fun nextLong(): Long = counter.incrementAndGet()
	
	/**
	 * 获取 36 进制的字符串 id。
	 */
	fun nextString(): String {
		return counter.incrementAndGet().toString(36)
	}
}
