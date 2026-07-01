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
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object ModelI18n {
	@AutoService(I18nDef::class)
	class Description : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "管理模型配置",
	)
	
	@AutoService(I18nDef::class)
	class ParamList : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "列出所有模型",
	)
	
	@AutoService(I18nDef::class)
	class ParamAdd : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "添加模型",
	)
	
	@AutoService(I18nDef::class)
	class ParamName : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "模型的显示名称",
	)
	
	@AutoService(I18nDef::class)
	class ParamProvider : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "模型的提供商名称",
	)
	
	@AutoService(I18nDef::class)
	class ParamAddInfo : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "使用指定模型ID的默认元数据",
	)
	
	@AutoService(I18nDef::class)
	class ParamAddAll : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "添加指定提供商下的所有模型",
	)
	
	@AutoService(I18nDef::class)
	class ParamRemove : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "删除指定模型",
	)
	
	@AutoService(I18nDef::class)
	class ParamDefault : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "设置指定模型为一些模型无法解析时的回退模型",
	)
	
	@AutoService(I18nDef::class)
	class ProviderNotFound : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "未找到名为 %s 的提供商",
	)
	
	@AutoService(I18nDef::class)
	class ModelNotFound : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "未找到名为 %s 的模型",
	)
	
	@AutoService(I18nDef::class)
	class ModelDuplicateError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "相同提供商下已经存在名称为 %s 的模型了",
	)
	
	@AutoService(I18nDef::class)
	class PromptId : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "请输入模型ID (如deepseek-v4-pro):",
	)
	
	@AutoService(I18nDef::class)
	class PromptContextWindow : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "请输入模型的上下文窗口 $TOKENS:",
	)
	
	@AutoService(I18nDef::class)
	class PromptMaxOutputTokens : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "请输入模型的最大输出长度 $TOKENS:",
	)
	
	@AutoService(I18nDef::class)
	class PromptSetInputPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "是否开始设置模型输入价格 $YON:",
	)
	
	@AutoService(I18nDef::class)
	class PromptSetOutputPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "是否开始设置模型输出价格 $YON:",
	)
	
	@AutoService(I18nDef::class)
	class PromptTieredPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "是否为阶梯计费 $YON:",
	)
	
	@AutoService(I18nDef::class)
	class PromptSetCachedPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "是否设置命中缓存的价格 $YON:",
	)
	
	@AutoService(I18nDef::class)
	class PromptSetPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "是否要继续添加一个价格区间 $YON:",
	)
	
	@AutoService(I18nDef::class)
	class PromptSetFeature : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "模型是否支持[%s] $YON:",
	)
	
	@AutoService(I18nDef::class)
	class InvalidValue : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "无效的值",
	)
	
	@AutoService(I18nDef::class)
	class PromptPriceRange : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "输入价格区间，格式为 [起始 $TOKENS + '-' + 结束 $TOKENS]，仅输入一个数将视为最后一个区间的开始值\n区间:",
	)
	
	@AutoService(I18nDef::class)
	class PromptTokenUnit : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "价格单位 (每多少 tokens)，例：每百万 tokens 10 美元，单位为 '1,000,000'\n单位:",
	)
	
	@AutoService(I18nDef::class)
	class PromptPriceCurrency : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "货币代码，必须为三位英文字母:",
	)
	
	@AutoService(I18nDef::class)
	class PromptPrice : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "输入价格 (%s):",
	)
	
	@AutoService(I18nDef::class)
	class Unknown : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "未知",
	)
	
	const val TOKENS = "(tokens)"
	const val YON = "(y/n)"
}
