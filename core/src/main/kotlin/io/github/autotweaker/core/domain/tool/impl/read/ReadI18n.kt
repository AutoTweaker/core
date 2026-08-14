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

package io.github.autotweaker.core.domain.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object ReadI18n {
	@AutoService(I18nDef::class)
	class InvalidArg : I18nBase(
		zh("读取文件失败，非法的请求参数"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidPath : I18nBase(
		zh("读取文件失败，非法的路径：%s"),
	)
	
	@AutoService(I18nDef::class)
	class TooManyLines : I18nBase(
		zh("读取文件 %s 失败，请求的行数过多：%s"),
	)
	
	@AutoService(I18nDef::class)
	class TooFewChars : I18nBase(
		zh("读取文件 %s 失败，用于总结的字符数过少：%s"),
	)
	
	@AutoService(I18nDef::class)
	class FileNotFound : I18nBase(
		zh("读取文件失败，找不到 %s"),
	)
	
	@AutoService(I18nDef::class)
	class FileNotRegular : I18nBase(
		zh("读取文件失败，%s 不是普通文件"),
	)
	
	@AutoService(I18nDef::class)
	class PathOutsideWorkspace : I18nBase(
		zh("读取文件失败，%s 不在容器工作区内"),
	)
	
	@AutoService(I18nDef::class)
	class Request : I18nBase(
		zh("请求读取 %s（%s）"),
	)
	
	@AutoService(I18nDef::class)
	class RequestSummary : I18nBase(
		zh("请求读取并总结 %s（%s）"),
	)
	
	@AutoService(I18nDef::class)
	class Executing : I18nBase(
		zh("正在读取 %s"),
	)
	
	@AutoService(I18nDef::class)
	class ExecutingSummary : I18nBase(
		zh("正在读取并总结 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Cancelled : I18nBase(
		zh("读取 %s 被取消"),
	)
	
	@AutoService(I18nDef::class)
	class Rejected : I18nBase(
		zh("读取 %s 被拒绝"),
	)
	
	@AutoService(I18nDef::class)
	class RejectedWithReason : I18nBase(
		zh("读取 %s 被拒绝：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Failed : I18nBase(
		zh("读取 %s 失败：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Timeout : I18nBase(
		zh("读取 %s 超时：%s"),
	)
	
	@AutoService(I18nDef::class)
	class StartLineError : I18nBase(
		zh("读取文件 %s 失败，请求的起始行 %s 超出了文件总行数 %s"),
	)
	
	@AutoService(I18nDef::class)
	class AccessDenied : I18nBase(
		zh("读取文件 %s 失败，权限不足"),
	)
	
	@AutoService(I18nDef::class)
	class SummaryFailed : I18nBase(
		zh("读取文件 %s 失败，总结器出错：%s"),
	)
	
	@AutoService(I18nDef::class)
	class Executed : I18nBase(
		zh("读取了 %s"),
	)
	
	@AutoService(I18nDef::class)
	class ExecutedSummary : I18nBase(
		zh("读取并总结了 %s"),
	)
}
