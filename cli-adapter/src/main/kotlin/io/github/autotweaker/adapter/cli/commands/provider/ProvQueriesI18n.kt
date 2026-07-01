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
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object ProvQueriesI18n {
	@AutoService(I18nDef::class)
	class Name : I18nBase(
		Locale.ENGLISH to "Name: %s",
		Locale.SIMPLIFIED_CHINESE to "名称: %s",
	)
	
	@AutoService(I18nDef::class)
	class Type : I18nBase(
		Locale.ENGLISH to "Type: %s",
		Locale.SIMPLIFIED_CHINESE to "类型: %s",
	)
	
	@AutoService(I18nDef::class)
	class Model : I18nBase(
		Locale.ENGLISH to "Models: %s",
		Locale.SIMPLIFIED_CHINESE to "模型: %s",
	)
	
	@AutoService(I18nDef::class)
	class Key : I18nBase(
		Locale.ENGLISH to "Key: %s",
		Locale.SIMPLIFIED_CHINESE to "密钥: %s",
	)
	
	@AutoService(I18nDef::class)
	class Url : I18nBase(
		Locale.ENGLISH to "API endpoint: %s",
		Locale.SIMPLIFIED_CHINESE to "API端点: %s",
	)
	
	@AutoService(I18nDef::class)
	class Rule : I18nBase(
		Locale.ENGLISH to "Error handling rules:",
		Locale.SIMPLIFIED_CHINESE to "错误处理规则:",
	)
	
	@AutoService(I18nDef::class)
	class Default : I18nBase(
		Locale.ENGLISH to "[default]",
		Locale.SIMPLIFIED_CHINESE to "[默认]",
	)
	
	@AutoService(I18nDef::class)
	class StatusCode : I18nBase(
		Locale.ENGLISH to "Status code: %s",
		Locale.SIMPLIFIED_CHINESE to "状态码: %s",
	)
	
	@AutoService(I18nDef::class)
	class Strategy : I18nBase(
		Locale.ENGLISH to "Strategy: %s",
		Locale.SIMPLIFIED_CHINESE to "策略: %s",
	)
	
	@AutoService(I18nDef::class)
	class ModelId : I18nBase(
		Locale.ENGLISH to "Model ID: %s",
		Locale.SIMPLIFIED_CHINESE to "模型ID: %s",
	)
	
	@AutoService(I18nDef::class)
	class ContextWindow : I18nBase(
		Locale.ENGLISH to "Context window: %s",
		Locale.SIMPLIFIED_CHINESE to "上下文窗口: %s",
	)
	
	@AutoService(I18nDef::class)
	class MaxOutput : I18nBase(
		Locale.ENGLISH to "Max output: %s",
		Locale.SIMPLIFIED_CHINESE to "最大输出长度: %s",
	)
	
	@AutoService(I18nDef::class)
	class ModelFeature : I18nBase(
		Locale.ENGLISH to "Features: %s",
		Locale.SIMPLIFIED_CHINESE to "能力: %s",
	)
	
	@AutoService(I18nDef::class)
	class InputPrice : I18nBase(
		Locale.ENGLISH to "Input price:",
		Locale.SIMPLIFIED_CHINESE to "输入价格:",
	)
	
	@AutoService(I18nDef::class)
	class CachedPrice : I18nBase(
		Locale.ENGLISH to "(cached)",
		Locale.SIMPLIFIED_CHINESE to "(命中缓存)",
	)
	
	@AutoService(I18nDef::class)
	class OutputPrice : I18nBase(
		Locale.ENGLISH to "Output price:",
		Locale.SIMPLIFIED_CHINESE to "输出价格:",
	)
	
	@AutoService(I18nDef::class)
	class Or : I18nBase(
		Locale.ENGLISH to "or",
		Locale.SIMPLIFIED_CHINESE to "或",
	)
}
