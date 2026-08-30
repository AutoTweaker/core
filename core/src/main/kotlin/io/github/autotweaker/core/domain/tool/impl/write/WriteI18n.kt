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

package io.github.autotweaker.core.domain.tool.impl.write

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object WriteI18n {
	@AutoService(I18nDef::class)
	class Create : I18nBase(
		zh("创建"),
	)
	
	@AutoService(I18nDef::class)
	class Update : I18nBase(
		zh("更新"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidPath : I18nBase(
		zh("创建或更新文件失败，非法的路径：%s"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidHashArg : I18nBase(
		zh("更新文件 %s 失败，非法的请求参数"),
	)
	
	@AutoService(I18nDef::class)
	class CreateFailedExists : I18nBase(
		zh("创建文件 %s 失败，文件已存在"),
	)
	
	@AutoService(I18nDef::class)
	class UpdateFailedChanged : I18nBase(
		zh("更新文件 %s 失败，文件已被外部更改"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidEscape : I18nBase(
		zh("创建或更新文件 %s 失败，非法的转义"),
	)
	
	@AutoService(I18nDef::class)
	class Request : I18nBase(
		zh("请求%s %s（%s）"),
	)
	
	@AutoService(I18nDef::class)
	class Executing : I18nBase(
		zh("正在%s %s"),
	)
	
	@AutoService(I18nDef::class)
	class Cancelled : I18nBase(
		zh("%s %s 被取消"),
	)
	
	@AutoService(I18nDef::class)
	class Rejected : I18nBase(
		zh("%s %s 被拒绝"),
	)
	
	@AutoService(I18nDef::class)
	class RejectedWithReason : I18nBase(
		zh("%s %s 被拒绝：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Failed : I18nBase(
		zh("%s %s 失败：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Timeout : I18nBase(
		zh("%s %s 超时：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Created : I18nBase(
		zh("创建了 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Updated : I18nBase(
		zh("更新了 %s"),
	)
}
