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

package io.github.autotweaker.core.domain.port.exception

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object PortExceptionI18n {
	@AutoService(I18nDef::class)
	class FileAccessDeniedException : I18nBase(
		zh("当前用户没有权限读取这个文件"),
	)
	
	@AutoService(I18nDef::class)
	class FileAlreadyExistsException : I18nBase(
		zh("文件已经存在"),
	)
	
	@AutoService(I18nDef::class)
	class FileNotFoundException : I18nBase(
		zh("文件不存在"),
	)
	
	@AutoService(I18nDef::class)
	class FileNotWritableException : I18nBase(
		zh("文件不可写"),
	)
	
	@AutoService(I18nDef::class)
	class FileChangedException : I18nBase(
		zh("文件 SHA-256 不符合预期，文件已经被更新"),
	)
}
