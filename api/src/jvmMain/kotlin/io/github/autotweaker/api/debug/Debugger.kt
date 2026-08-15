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

package io.github.autotweaker.api.debug

/**
 * 实现此接口来获取项目 H2 数据库的底层读写能力，不经过任何校验。
 *
 * 除非用于开发的调试需要，否则不应实现此接口，也不应将 [DbDebugAPI] 的能力暴露给普通用户。
 *
 * [io.github.autotweaker.api.debug]、[io.github.autotweaker.api.types.debug] 下的接口和数据类随时变更而不一定通过版本号或更新日志反映。
 *
 * 需要打上 `@AutoService(Debugger::class)` 来让 AutoTweaker 发现。
 */
interface Debugger {
	suspend fun init(api: DbDebugAPI)
}
