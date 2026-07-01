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

package io.github.autotweaker.adapter.cli.commands.trace

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object TraceI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "查看程序的数据记录",
	)
	
	@AutoService(I18nDef::class)
	class ListDesc : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "列出所有记录来源及命名空间及条目数量",
	)
	
	@AutoService(I18nDef::class)
	class Show : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "读取记录",
	)
	
	@AutoService(I18nDef::class)
	class Origin : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "记录来源",
	)
	
	@AutoService(I18nDef::class)
	class Namespace : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "命名空间",
	)
	
	@AutoService(I18nDef::class)
	class Range : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "范围，例如 '0-20'，或者'20'表示'20-20'",
	)
	
	@AutoService(I18nDef::class)
	class InvalidValue : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "无效的值，请检查命令参数",
	)
}
