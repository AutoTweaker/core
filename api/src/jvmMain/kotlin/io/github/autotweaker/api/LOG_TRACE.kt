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

import org.slf4j.LoggerFactory

/**
 * 打印一条用于临时调试的 error 级别日志，特殊的命名便于通过自动化脚本扫描。
 *
 * 要防止误提交到 git，可以在 `.git/hooks/pre-commit` 中扫描 `_LOG_TRACE_` 是否出现在代码中。
 */
@Deprecated("DO NOT COMMIT THIS", level = DeprecationLevel.WARNING)
@Suppress("FunctionName")
fun Loggable._LOG_TRACE_(content: String) = LoggerFactory.getLogger(this::class.java).error(content)
