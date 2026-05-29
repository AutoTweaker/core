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

package io.github.autotweaker.adapter.cli.commands.model

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

internal object ModelI18n {
	@AutoService(I18nDef::class)
	class Description : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理模型配置"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamList : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出所有模型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAdd : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "添加模型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAddName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型的显示名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAddProvider : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型的提供商名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAddId : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型的ID，如deepseek-v4-pro"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAddAll : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "添加指定提供商下的所有模型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ProviderNotFound : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "未找到名为 %s 的提供商"),
		)
	}
}
