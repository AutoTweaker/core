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

package io.github.autotweaker.adapter.cli.commands.session.usage

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object SessionUsageI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("查看大模型用量信息，无参查看全部"),
	)
	
	@AutoService(I18nDef::class)
	class NoUsage : I18nBase(
		zh("并未产生任何用量"),
	)
	
	@AutoService(I18nDef::class)
	class TotalTokens : I18nBase(
		zh("总计: %s tokens"),
	)
	
	@AutoService(I18nDef::class)
	class PromptTokens : I18nBase(
		zh("总输入: %s tokens"),
	)
	
	@AutoService(I18nDef::class)
	class CompletionTokens : I18nBase(
		zh("总输出: %s tokens"),
	)
	
	@AutoService(I18nDef::class)
	class ReasoningTokens : I18nBase(
		zh("推理用量: %s tokens"),
	)
	
	@AutoService(I18nDef::class)
	class CacheHitRate : I18nBase(
		zh("缓存命中率: %s"),
	)
}
