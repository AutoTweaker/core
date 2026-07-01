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

package io.github.autotweaker.adapter.cli.commands.provider

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object ProvQueriesI18n {
	@AutoService(I18nDef::class)
	class Name : I18nBase(
		en("Name: %s"),
		zh("名称: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Type : I18nBase(
		en("Type: %s"),
		zh("类型: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Model : I18nBase(
		en("Models: %s"),
		zh("模型: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Key : I18nBase(
		en("Key: %s"),
		zh("密钥: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Url : I18nBase(
		en("API endpoint: %s"),
		zh("API端点: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Rule : I18nBase(
		en("Error handling rules:"),
		zh("错误处理规则:"),
	)
	
	@AutoService(I18nDef::class)
	class Default : I18nBase(
		en("[default]"),
		zh("[默认]"),
	)
	
	@AutoService(I18nDef::class)
	class StatusCode : I18nBase(
		en("Status code: %s"),
		zh("状态码: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Strategy : I18nBase(
		en("Strategy: %s"),
		zh("策略: %s"),
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
	
	@AutoService(I18nDef::class)
	class InputPrice : I18nBase(
		en("Input price:"),
		zh("输入价格:"),
	)
	
	@AutoService(I18nDef::class)
	class CachedPrice : I18nBase(
		en("(cached)"),
		zh("(命中缓存)"),
	)
	
	@AutoService(I18nDef::class)
	class OutputPrice : I18nBase(
		en("Output price:"),
		zh("输出价格:"),
	)
	
	@AutoService(I18nDef::class)
	class Or : I18nBase(
		en("or"),
		zh("或"),
	)
}
