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

package io.github.autotweaker.api

/**
 * 为 [String] 添加 `*` 操作符的支持，例如 `"**" * 10`，输出 20 个 `*`。
 */
operator fun String.times(n: Int): String = repeat(n)

/**
 * 为 [Char] 添加 `*` 操作符的支持，例如 `'-' * 10` 输出 10 个 `-`
 */
operator fun Char.times(n: Int): String = toString().repeat(n)
