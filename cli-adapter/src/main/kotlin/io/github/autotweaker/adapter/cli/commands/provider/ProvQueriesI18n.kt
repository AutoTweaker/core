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
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object ProvQueriesI18n {
	@AutoService(I18nDef::class)
	class Name : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Name: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "名称: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Type : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Type: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "类型: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Model : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Models: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Key : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Key: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Url : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "API endpoint: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "API端点: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Rule : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Error handling rules:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "错误处理规则:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Default : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "[default]"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "[默认]"),
		)
	}
	
	@AutoService(I18nDef::class)
	class StatusCode : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Status code: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "状态码: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Strategy : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Strategy: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "策略: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ModelId : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Model ID: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型ID: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ContextWindow : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Context window: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "上下文窗口: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class MaxOutput : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Max output: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "最大输出长度: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ModelFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Features: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "能力: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InputPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Input price:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "输入价格:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class CachedPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "(cached)"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "(命中缓存)"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutputPrice : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Output price:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "输出价格:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Or : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "or"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "或"),
		)
	}
}
