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

package io.github.autotweaker.adapter.cli.commands

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object ModelFeature {
	@AutoService(I18nDef::class)
	class StreamingFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Streaming"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "流式输出"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ToolCallFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Tool calling"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "工具调用"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ReasoningFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Reasoning"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "深度思考"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ImageFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Image understanding"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "图像理解"),
		)
	}
	
	@AutoService(I18nDef::class)
	class JsonOutputFeature : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "JSON output"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "格式化输出"),
		)
	}
}
