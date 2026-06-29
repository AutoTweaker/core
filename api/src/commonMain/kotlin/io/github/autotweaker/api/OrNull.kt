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
 * 如果列表为空，返回 null，否则返回列表本身。
 */
fun <T> List<T>.orNull() = ifEmpty { null }

/**
 * 如果字符串为空，返回 null，否则返回字符串本身。
 *
 * 判空使用 [ifEmpty]（不是 [ifBlank]）。
 */
fun String.orNull() = ifEmpty { null }
