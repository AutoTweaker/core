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

@file:Suppress("NOTHING_TO_INLINE", "UnusedReceiverParameter")

package io.github.autotweaker.api

/**
 * 丢弃对象并返回 [Unit]。
 *
 * 常用于链式调用末尾吞掉表达式返回值。
 */
inline fun <T> T.discard(): Unit = Unit

/**
 * 丢弃对象并返回 [result]。
 *
 * 常用于链式调用末尾吞掉返回值并返回 `null`。
 */
inline fun <T, R> T.discard(result: R): R = result

/**
 * 用于在编译期拦截对于 Unit 对象的冗余 [discard] 调用。
 */
@Deprecated(
	"discard() on Unit is redundant",
	replaceWith = ReplaceWith("this"),
	level = DeprecationLevel.ERROR
)
inline fun Unit.discard(): Unit = throw NotImplementedError()
