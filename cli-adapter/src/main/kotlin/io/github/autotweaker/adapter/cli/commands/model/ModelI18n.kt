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
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object ModelI18n {
	@AutoService(I18nDef::class)
	class Description : I18nBase(
		zh("管理模型配置")
	)
	
	@AutoService(I18nDef::class)
	class ParamList : I18nBase(
		zh("列出所有模型")
	)
	
	@AutoService(I18nDef::class)
	class ParamAdd : I18nBase(
		zh("添加模型")
	)
	
	@AutoService(I18nDef::class)
	class ParamName : I18nBase(
		zh("模型的显示名称")
	)
	
	@AutoService(I18nDef::class)
	class ParamProvider : I18nBase(
		zh("模型的提供商名称")
	)
	
	@AutoService(I18nDef::class)
	class ParamAddInfo : I18nBase(
		zh("使用指定模型ID的默认元数据")
	)
	
	@AutoService(I18nDef::class)
	class ParamAddAll : I18nBase(
		zh("添加指定提供商下的所有模型")
	)
	
	@AutoService(I18nDef::class)
	class ParamRemove : I18nBase(
		zh("删除指定模型")
	)
	
	@AutoService(I18nDef::class)
	class ParamShow : I18nBase(
		zh("显示模型信息")
	)
	
	@AutoService(I18nDef::class)
	class ParamDefault : I18nBase(
		zh("设置指定模型为默认模型")
	)
	
	@AutoService(I18nDef::class)
	class ProviderNotFound : I18nBase(
		zh("未找到名为 %s 的提供商")
	)
	
	@AutoService(I18nDef::class)
	class ModelNotFound : I18nBase(
		zh("未找到名为 %s 的模型")
	)
	
	@AutoService(I18nDef::class)
	class ModelDuplicateError : I18nBase(
		zh("相同提供商下已经存在名称为 %s 的模型了")
	)
	
	@AutoService(I18nDef::class)
	class PromptId : I18nBase(
		zh("请输入模型ID (如deepseek-v4-pro):")
	)
	
	@AutoService(I18nDef::class)
	class PromptContextWindow : I18nBase(
		zh("请输入模型的上下文窗口 $TOKENS:")
	)
	
	@AutoService(I18nDef::class)
	class PromptMaxOutputTokens : I18nBase(
		zh("请输入模型的最大输出长度 $TOKENS:")
	)
	
	@AutoService(I18nDef::class)
	class PromptSetFeature : I18nBase(
		zh("模型是否支持[%s] $YON:")
	)
	
	@AutoService(I18nDef::class)
	class InvalidValue : I18nBase(
		zh("无效的值")
	)
	
	@AutoService(I18nDef::class)
	class ParamResetDefault : I18nBase(
		zh("重置默认模型")
	)
	
	@AutoService(I18nDef::class)
	class ParamGetDefault : I18nBase(
		zh("获取当前默认模型")
	)
	
	@AutoService(I18nDef::class)
	class Unknown : I18nBase(
		zh("未知")
	)
	
	@AutoService(I18nDef::class)
	class NotSet : I18nBase(
		zh("未设置")
	)
	
	@AutoService(I18nDef::class)
	class ModelName : I18nBase(
		zh("模型名称: %s"),
	)
	
	@AutoService(I18nDef::class)
	class ProviderName : I18nBase(
		zh("提供商名称: %s"),
	)
	
	@AutoService(I18nDef::class)
	class ModelId : I18nBase(
		en("Model ID: %s"),
		zh("模型ID: %s"),
	)
	
	@AutoService(I18nDef::class)
	class ContextWindow : I18nBase(
		en("Context window: %s"),
		zh("上下文窗口: %s"),
	)
	
	@AutoService(I18nDef::class)
	class MaxOutput : I18nBase(
		en("Max output: %s"),
		zh("最大输出长度: %s"),
	)
	
	@AutoService(I18nDef::class)
	class ModelFeature : I18nBase(
		en("Features: %s"),
		zh("能力: %s"),
	)
	
	const val TOKENS = "(tokens)"
	const val YON = "(y/n)"
}
