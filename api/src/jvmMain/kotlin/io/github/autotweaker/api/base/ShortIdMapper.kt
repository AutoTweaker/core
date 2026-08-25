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

import io.github.autotweaker.api.base.guava.biMapOf
import io.github.autotweaker.api.base.guava.inverse
import java.util.*

/**
 * 将 [UUID] 映射为短 id，在内存中保存映射。
 */
object ShortIdMapper {
	private val map = biMapOf<Long, UUID>()
	private val lock = ReentrantMutex()
	
	/**
	 * 获取 [id] 的 36 进制字符串短 id。
	 */
	suspend fun shortString(id: UUID): String = shortLong(id).toString(36)
	
	/**
	 * 获取 [id] 的数字短 id。
	 */
	suspend fun shortLong(id: UUID): Long = lock.withLock {
		map.inverse.getOrPut(id) {
			ShortIdGenerator.nextLong()
		}
	}
	
	/**
	 * 获取 36 进制字符串短 id 对应的 [UUID]。
	 */
	suspend fun uuid(short: String): UUID? = uuid(short.toLong(36))
	
	/**
	 * 获取短 id 对应的 [UUID]。
	 */
	suspend fun uuid(short: Long): UUID? = lock.withLock {
		map[short]
	}
}
