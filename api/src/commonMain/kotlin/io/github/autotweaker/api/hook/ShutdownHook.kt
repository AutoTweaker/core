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

/**
 * 程序关闭前会扫描所有 [ShutdownHook] 实现并并发调用。
 *
 * 时机为关闭刚开始，适配器全部停止，AutoTweaker 的所有服务仍然在运行。
 *
 * 实现 [ShutdownHook] 的类只有在程序关闭前才被实例化。
 *
 * 需要打上 `@AutoService(ShutdownHook::class)` 来让 AutoTweaker 发现。
 */
interface ShutdownHook {
	/**
	 * 程序即将关闭时必然调用此方法，除非被强杀。
	 */
	suspend fun shutdown()
}
