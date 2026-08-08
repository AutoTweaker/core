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

package io.github.autotweaker.adapter.cli.commands.secret.env

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object EnvI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理容器或暴露给大模型的环境变量")
	)
	
	@AutoService(I18nDef::class)
	class EnvType : I18nBase(
		zh("""指定环境变量的类型，可选值"container"/"bash""""),
	)
	
	@AutoService(I18nDef::class)
	class PromptInputEnv : I18nBase(
		zh("请输入环境变量 %s 的值:")
	)
	
	@AutoService(I18nDef::class)
	class EnvNotFoundError : I18nBase(
		zh("不存在名为 '%s' 的环境变量")
	)
}
