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

import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.SemVer.Companion.toSemVer

/**
 * 取所在包的 `Implementation-Version` 元数据。
 *
 * 配合 gradle 插件 `io.github.autotweaker.plugin.versioning` 使用，更丝滑。
 *
 * @throws IllegalStateException 取不到 `Implementation-Version`。
 * @throws IllegalArgumentException 取到的版本号字符串不符合 SemVer 规范。
 */
inline val <T : Any> T.appVersion: SemVer
	get() = javaClass.getPackage().implementationVersion?.toSemVer() ?: error("Missing Implementation-Version")
