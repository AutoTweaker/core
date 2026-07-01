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

package io.github.autotweaker.adapter.cli

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object CmdI18n {
	@AutoService(I18nDef::class)
	class UnknownHint : I18nBase(
		en($$"Unknown command: '%1$s'. Run '%2$s help'."),
		zh($$"未知的命令: %1$s。运行 %2$s help 查看可用命令"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidArgs : I18nBase(
		en($$"Invalid arguments. Run '%2$s help %1$s' for usage."),
		zh($$"无效的参数。运行 %2$s help %1$s 查看用法"),
	)
	
	@AutoService(I18nDef::class)
	class KeystoreLocked : I18nBase(
		zh("密钥库已锁定。运行 %s help 查看帮助"),
	)
}
