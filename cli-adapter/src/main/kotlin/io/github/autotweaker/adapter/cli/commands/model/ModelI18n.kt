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
	class ParamAddInfo : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "使用指定模型ID的默认元数据"),
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
	
	@AutoService(I18nDef::class)
	class ModelDuplicateError : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "相同提供商下已经存在名称为 %s 的模型了"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptId : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请输入模型ID (如deepseek-v4-pro):"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptContextWindow : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请输入模型的上下文窗口 $TOKENS:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptMaxOutputTokens : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请输入模型的最大输出长度 $TOKENS:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptSetInputPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "是否开始设置模型输入价格 $YON:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptSetOutputPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "是否开始设置模型输出价格 $YON:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptTieredPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "是否为阶梯计费 $YON:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptSetCachedPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "是否设置命中缓存的价格:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptSetPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "是否要继续添加一个价格区间 $YON:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptSetFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型是否支持[%s] $YON:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidValue : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "无效的值"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptPriceRange : I18nDef {
		override val localizations = listOf(
			LocalizedString(
				Locale.SIMPLIFIED_CHINESE,
				"输入价格区间，格式为 <起始 $TOKENS + '-' + 结束 $TOKENS>，仅输入一个数将视为最后一个区间\n区间:"
			),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptTokenUnit : I18nDef {
		override val localizations = listOf(
			LocalizedString(
				Locale.SIMPLIFIED_CHINESE,
				"价格基于什么单位 $TOKENS:"
			),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptPriceCurrency : I18nDef {
		override val localizations = listOf(
			LocalizedString(
				Locale.SIMPLIFIED_CHINESE,
				"货币代码，必须为三位英文字母:"
			),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(
				Locale.SIMPLIFIED_CHINESE,
				"输入价格 (%s):"
			),
		)
	}
	
	const val TOKENS = "(tokens)"
	const val YON = "(y/n)"
}
