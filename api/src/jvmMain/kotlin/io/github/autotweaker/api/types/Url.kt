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

package io.github.autotweaker.api.types

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.Url.Companion.toUrl
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * 表示一个不以 `/` 结尾的 url，协议为 http / https。
 */
@JvmInline
@Serializable
value class Url private constructor(val value: String) {
	companion object : Traceable {
		/**
		 * 从 [String] 构造 url，自动 [trim] 并移除尾部 `/`。
		 *
		 * @throws IllegalArgumentException url 为空、url 无效、url 的协议既不是 http 也不是 https。
		 */
		fun String.toUrl(): Url {
			val trimmed = trim().trimEnd('/')
			require(trimmed.isNotBlank()) { "URL must not be blank" }
			trace.catching { URI(trimmed) }.getOrNull()
				?.takeIf { it.isAbsolute && it.scheme in listOf("http", "https") }
				?: throw IllegalArgumentException("Invalid URL: $trimmed")
			return Url(trimmed)
		}
		
		/**
		 * @return 如果格式合法，返回 [Url]，否则返回 null。
		 * @see toUrl
		 */
		fun String.toUrlOrNull(): Url? = trace.catching { toUrl() }.getOrNull()
	}
}
