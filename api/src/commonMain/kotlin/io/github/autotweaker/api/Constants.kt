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
 * 值为 `AutoTweaker`。
 */
const val APP_NAME = "AutoTweaker"

/**
 * 值为 `autotweaker`。
 */
val APP_NAME_LOWERCASE = APP_NAME.lowercase()

/**
 * 一个空格字符（`' '`）。
 */
const val SPACE = ' '

/**
 * 四个空格字符（`"    "`）。
 */
val INDENT = SPACE * 4

/**
 * 星号，也就是 `*`。
 */
const val MASK_CHAR = '*'


/**
 * 长度为 10 的 `-`，也就是 `----------`。
 */
val LINE = line(10)
