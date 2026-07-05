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

package io.github.autotweaker.api.i18n


import io.github.autotweaker.api.types.Localizations

/**
 * 实现此接口并打上 `@AutoService(I18nDef::class)` 注释即可注册一个 i18n 条目。
 *
 * 继承 [io.github.autotweaker.api.base.I18nBase] 可以省去一些声明 i18n 的重复代码。
 *
 * 实现 [io.github.autotweaker.api.I18nable] 接口即可通过 [I18nService] 获取设置的当前值。
 *
 * 通过 [io.github.autotweaker.api.adapter.CoreAPI.I18nAPI] 可管理所有 i18n 条目。
 *
 * AutoTweaker 通过 SPI 和 [I18nService] 来实现分布式的 i18n 声明以及安全的取值。
 *
 * AutoTweaker 会自动翻译 i18n 条目，所以不必注册很多语言的条目。
 */
interface I18nDef {
	val localizations: Localizations
}
