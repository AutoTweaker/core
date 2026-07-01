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
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object ModelFeature {
	@AutoService(I18nDef::class)
	class StreamingFeature : I18nBase(
		Locale.ENGLISH to "Streaming",
		Locale.SIMPLIFIED_CHINESE to "流式输出",
	)
	
	@AutoService(I18nDef::class)
	class ToolCallFeature : I18nBase(
		Locale.ENGLISH to "Tool calling",
		Locale.SIMPLIFIED_CHINESE to "工具调用",
	)
	
	@AutoService(I18nDef::class)
	class ReasoningFeature : I18nBase(
		Locale.ENGLISH to "Reasoning",
		Locale.SIMPLIFIED_CHINESE to "深度思考",
	)
	
	@AutoService(I18nDef::class)
	class ImageFeature : I18nBase(
		Locale.ENGLISH to "Image understanding",
		Locale.SIMPLIFIED_CHINESE to "图像理解",
	)
	
	@AutoService(I18nDef::class)
	class JsonOutputFeature : I18nBase(
		Locale.ENGLISH to "JSON output",
		Locale.SIMPLIFIED_CHINESE to "格式化输出",
	)
}
