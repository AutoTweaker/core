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
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object HelpI18n {
	@AutoService(I18nDef::class)
	class HelpDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Show available commands and their usage"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出可用命令"),
		)
	}
	
	@AutoService(I18nDef::class)
	class HelpParamCommand : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Show help for a specific command"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "查看指定命令的用法"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Unknown : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Unknown command: '%s'"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "未知的命令: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Available : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Available commands:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "可用命令:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class HelpHint : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Run '%s help <command>' for detailed usage."),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "运行 %s help <command> 查看用法"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Params : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Parameters:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "参数:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamOptional : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "(optional)"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "(可选)"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SyntaxXorLabel : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "[choose one]"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "[任选其一]"),
		)
	}
}
