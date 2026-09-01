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

package io.github.autotweaker.core.domain.tool.impl.edit

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object EditI18n {
	@AutoService(I18nDef::class)
	class InvalidPath : I18nBase(
		zh("编辑文件失败，非法的路径：%s"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidArg : I18nBase(
		zh("编辑文件 %s 失败，非法的请求参数"),
	)
	
	@AutoService(I18nDef::class)
	class ReadFailed : I18nBase(
		zh("编辑文件 %s 失败，无法读取目标文件"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidEscape : I18nBase(
		zh("编辑文件 %s 失败，非法的转义"),
	)
	
	@AutoService(I18nDef::class)
	class NoMatch : I18nBase(
		zh("编辑文件 %s 失败，无匹配内容"),
	)
	
	@AutoService(I18nDef::class)
	class NotUnique : I18nBase(
		zh("编辑文件 %s 失败，匹配项不唯一"),
	)
	
	@AutoService(I18nDef::class)
	class Request : I18nBase(
		zh("请求编辑 %s（%s）"),
	)
	
	@AutoService(I18nDef::class)
	class Executing : I18nBase(
		zh("正在编辑 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Cancelled : I18nBase(
		zh("编辑 %s 被取消"),
	)
	
	@AutoService(I18nDef::class)
	class Rejected : I18nBase(
		zh("编辑 %s 被拒绝"),
	)
	
	@AutoService(I18nDef::class)
	class RejectedWithReason : I18nBase(
		zh("编辑 %s 被拒绝：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Failed : I18nBase(
		zh("编辑 %s 失败：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Timeout : I18nBase(
		zh("编辑 %s 超时：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Updated : I18nBase(
		zh("编辑了 %s"),
	)
}
