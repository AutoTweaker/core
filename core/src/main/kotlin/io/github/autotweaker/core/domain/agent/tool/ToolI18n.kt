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

package io.github.autotweaker.core.domain.agent.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object ToolI18n {
	@AutoService(I18nDef::class)
	class Activation : I18nBase(
		zh("激活了 %s 工具"),
	)
	
	@AutoService(I18nDef::class)
	class ResolveError : I18nBase(
		zh("调用 %s 工具失败，参数解析错误：%s"),
	)
	
	@AutoService(I18nDef::class)
	class JsonParseError : I18nBase(
		zh("调用 %s 工具失败，非法的 JSON 对象"),
	)
	
	@AutoService(I18nDef::class)
	class ArgumentsError : I18nBase(
		zh("调用 %s 工具失败，非法的工具参数"),
	)
	
	@AutoService(I18nDef::class)
	class NotFoundError : I18nBase(
		zh("调用 %s 工具失败，不存在的工具"),
	)
	
	@AutoService(I18nDef::class)
	class AlreadyActive : I18nBase(
		zh("激活 %s 工具失败，不能重复激活"),
	)
	
	@AutoService(I18nDef::class)
	class Cancelled : I18nBase(
		zh("调用 %s 工具被取消"),
	)
}
