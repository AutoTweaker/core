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
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object TraceI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "查看程序的数据记录"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ListOriginDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出所有记录来源"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ListNamespaceDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出指定记录来源下的命名空间及命名空间下条目数量"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Show : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "读取记录"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Origin : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "记录来源"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Namespace : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "命名空间"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Range : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "范围，例如 '0-20'"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidValue : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "无效的值，请检查命令参数"),
		)
	}
}
