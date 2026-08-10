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

import io.github.autotweaker.api.types.exception.AutoTweakerException
import io.github.autotweaker.api.types.exception.I18nableException

/**
 * 格式化异常信息，包括类名、message、cause。
 *
 * 如果为 [AutoTweakerException] 会直接使用其本地化字符串。
 */
fun Throwable.message(): String = if (this is I18nableException) message() else buildMessage()
