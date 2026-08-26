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

package io.github.autotweaker.core.domain.tool.impl.bash

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object BashI18n {
	@AutoService(I18nDef::class)
	class InvalidArg : I18nBase(
		zh("执行命令失败，非法的请求参数"),
	)
	
	@AutoService(I18nDef::class)
	class Request : I18nBase(
		zh("请求执行 Bash 命令（%s）"),
	)
	
	@AutoService(I18nDef::class)
	class RequestWithEnv : I18nBase(
		zh("请求执行 Bash 命令（%s），携带环境变量 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Executing : I18nBase(
		zh("正在执行 Bash 命令"),
	)
	
	@AutoService(I18nDef::class)
	class ExecutingWithEnv : I18nBase(
		zh("正在执行 Bash 命令，携带环境变量 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Cancelled : I18nBase(
		zh("执行 Bash 命令被取消"),
	)
	
	@AutoService(I18nDef::class)
	class Rejected : I18nBase(
		zh("执行 Bash 命令被拒绝"),
	)
	
	@AutoService(I18nDef::class)
	class RejectedWithReason : I18nBase(
		zh("执行 Bash 命令被拒绝：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Failed : I18nBase(
		zh("执行 Bash 命令失败：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Timeout : I18nBase(
		zh("执行 Bash 命令超时：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Executed : I18nBase(
		zh("执行了一条 Bash 命令"),
	)
	
	@AutoService(I18nDef::class)
	class ExecutedWithEnv : I18nBase(
		zh("执行了一条 Bash 命令，携带环境变量 %s"),
	)
}
