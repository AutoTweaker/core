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

package io.github.autotweaker.adapter.cli.commands.translate

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object TranslateI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理国际化服务，未指定参数时触发自动翻译"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SetModelDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "指定用于国际化翻译的模型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class RemoveModelDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "移除用于国际化翻译的模型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SetLanguageDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "指定国际化的目标语言，必须为 BCP 47 格式"),
		)
	}
}
