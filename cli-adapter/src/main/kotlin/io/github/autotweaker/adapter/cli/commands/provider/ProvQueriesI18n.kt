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

internal object ProvQueriesI18n {
	@AutoService(I18nDef::class)
	class OutName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Name: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "名称: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Type: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "类型: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModel : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Models: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Key: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutUrl : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "API endpoint: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "API端点: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutRule : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Error handling rules:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "错误处理规则:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutDefault : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "[default]"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "[默认]"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutRuleStatus : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Status code: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "状态码: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutRuleStrategy : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Strategy: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "策略: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelId : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Model ID: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "模型ID: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelContextWindow : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Context window: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "上下文窗口: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelMaxOutput : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Max output: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "最大输出长度: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Features: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "能力: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeatureStreaming : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Streaming"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "流式输出"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeatureToolCall : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Tool calling"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "工具调用"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeatureReasoning : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Reasoning"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "深度思考"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeatureImage : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Image understanding"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "图像理解"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelFeatureJsonOutput : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "JSON output"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "格式化输出"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelPriceInput : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Input price:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "输入价格:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelPriceCached : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "(cached)"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "(命中缓存)"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutModelPriceOutput : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Output price:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "输出价格:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutOr : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "or"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "或"),
		)
	}
}
