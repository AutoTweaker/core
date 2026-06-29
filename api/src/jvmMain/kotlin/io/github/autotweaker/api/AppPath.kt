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

import java.nio.file.Path

/**
 * 存储配置及其他数据的目录，请使用提供的持久化 API 而不是直接在此路径下存放文件
 */
val CONFIG_PATH: Path = Path.of(
	System.getProperty("user.home"), ".config", APP_NAME_LOWERCASE
)

/**
 * 存放临时文件的目录，通常为 `/tmp/autotweaker`
 */
val TMP_PATH: Path = Path.of(
	System.getProperty("java.io.tmpdir"), APP_NAME_LOWERCASE
)
