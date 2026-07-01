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

package io.github.autotweaker.adapter.cli.commands.help

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object HelpI18n {
	@AutoService(I18nDef::class)
	class HelpDesc : I18nBase(
		Locale.ENGLISH to "Show available commands and their usage",
		Locale.SIMPLIFIED_CHINESE to "列出可用命令",
	)
	
	@AutoService(I18nDef::class)
	class HelpParamCommand : I18nBase(
		Locale.ENGLISH to "Show help for a specific command",
		Locale.SIMPLIFIED_CHINESE to "查看指定命令的用法",
	)
	
	@AutoService(I18nDef::class)
	class Unknown : I18nBase(
		Locale.ENGLISH to "Unknown command: '%s'",
		Locale.SIMPLIFIED_CHINESE to "未知的命令: %s",
	)
	
	@AutoService(I18nDef::class)
	class Available : I18nBase(
		Locale.ENGLISH to "Available commands:",
		Locale.SIMPLIFIED_CHINESE to "可用命令:",
	)
	
	@AutoService(I18nDef::class)
	class HelpHint : I18nBase(
		Locale.ENGLISH to "Run '%s help <command>' for detailed usage.",
		Locale.SIMPLIFIED_CHINESE to "运行 %s help <command> 查看用法",
	)
	
	@AutoService(I18nDef::class)
	class Params : I18nBase(
		Locale.ENGLISH to "Parameters:",
		Locale.SIMPLIFIED_CHINESE to "参数:",
	)
	
	@AutoService(I18nDef::class)
	class ParamOptional : I18nBase(
		Locale.ENGLISH to "(optional)",
		Locale.SIMPLIFIED_CHINESE to "(可选)",
	)
	
	@AutoService(I18nDef::class)
	class SyntaxXorLabel : I18nBase(
		Locale.ENGLISH to "[choose one]",
		Locale.SIMPLIFIED_CHINESE to "[任选其一]",
	)
}
