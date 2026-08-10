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

package io.github.autotweaker.adapter.cli.commands.util.unicode

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.toUnicodeEscape
import io.github.autotweaker.api.unescapeUnicode

class Unicode : Command {
	override val name = "unicode"
	override val description = i18n(Desc())
	override val syntax = buildSyntax(XOR) {
		flag("escape", Escape())
		flag("unescape", Unescape())
	}
	override val requiresKeystore = false
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		val string = stdin ?: prompt(">")
		handleFlag("escape") {
			out(string.toUnicodeEscape())
		}
		handleFlag("unescape") {
			out(string.unescapeUnicode(strict = false)) {
				newline = false
			}
		}
		done(1)
	}
	
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("Unicode转义或解析，支持从stdin读取"),
	)
	
	@AutoService(I18nDef::class)
	class Escape : I18nBase(
		zh("对输入的内容进行Unicode转义"),
	)
	
	@AutoService(I18nDef::class)
	class Unescape : I18nBase(
		zh("解析输入内容中的Unicode转义序列"),
	)
}
