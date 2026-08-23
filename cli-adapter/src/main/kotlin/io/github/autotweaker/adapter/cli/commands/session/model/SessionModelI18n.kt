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

package io.github.autotweaker.adapter.cli.commands.session.model

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object SessionModelI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理新会话使用的模型"),
	)
	
	@AutoService(I18nDef::class)
	class GetDesc : I18nBase(
		zh("获取当前的新会话模型配置"),
	)
	
	@AutoService(I18nDef::class)
	class SetDesc : I18nBase(
		zh("设置新会话使用的模型，无-s等flag时设置主模型"),
	)
	
	@AutoService(I18nDef::class)
	class UseGetOrSet : I18nBase(
		zh("请使用 get / set"),
	)
	
	@AutoService(I18nDef::class)
	class MainModel : I18nBase(
		zh("主模型: "),
	)
	
	@AutoService(I18nDef::class)
	class Reasoning : I18nBase(
		zh("推理强度: %s"),
	)
	
	@AutoService(I18nDef::class)
	class SummarizeModel : I18nBase(
		zh("总结模型: "),
	)
	
	@AutoService(I18nDef::class)
	class CompactModel : I18nBase(
		zh("压缩模型: "),
	)
	
	@AutoService(I18nDef::class)
	class FallbackModel : I18nBase(
		zh("回退模型: ====="),
	)
	
	@AutoService(I18nDef::class)
	class ThinkingParam : I18nBase(
		zh("传递1或true启用思考，反之禁用"),
	)
	
	@AutoService(I18nDef::class)
	class SummarizeParam : I18nBase(
		zh("设置总结模型"),
	)
	
	@AutoService(I18nDef::class)
	class CompactParam : I18nBase(
		zh("设置上下文压缩模型"),
	)
	
	@AutoService(I18nDef::class)
	class AddFallbackParam : I18nBase(
		zh("添加一个回退模型"),
	)
	
	@AutoService(I18nDef::class)
	class RemoveFallbackParam : I18nBase(
		zh("移除一个回退模型"),
	)
}
