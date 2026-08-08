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

package io.github.autotweaker.adapter.cli.commands.secret.key

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object KeyI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理提供商密钥")
	)
	
	@AutoService(I18nDef::class)
	class EmptyNameError : I18nBase(
		zh("条目名称不能为空")
	)
	
	@AutoService(I18nDef::class)
	class EmptyKeyError : I18nBase(
		zh("密钥内容不能为空")
	)
	
	@AutoService(I18nDef::class)
	class KeyExistsError : I18nBase(
		zh("名为 '%s' 的密钥已存在")
	)
	
	@AutoService(I18nDef::class)
	class KeyNotFoundError : I18nBase(
		zh("不存在名为 '%s' 的密钥")
	)
	
	@AutoService(I18nDef::class)
	class PromptInputApiKey : I18nBase(
		zh("请输入密钥内容:")
	)
}
