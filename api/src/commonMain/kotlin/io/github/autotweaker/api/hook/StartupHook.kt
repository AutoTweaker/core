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

package io.github.autotweaker.api.hook

import io.github.autotweaker.api.types.SemVer

/**
 * 程序刚刚启动，拿到锁（`~/.config/autotweaker/autotweaker.lock`）后立即调用，此时一切服务都未初始化。
 *
 * 需要打上 `@AutoService(StartupHook::class)` 来让 AutoTweaker 发现。
 */
interface StartupHook {
	suspend fun execute(coreVersion: SemVer)
}
