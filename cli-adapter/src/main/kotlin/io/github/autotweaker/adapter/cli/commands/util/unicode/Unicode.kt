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
import io.github.autotweaker.api.unreachable

class Unicode : Command {
	override val name = "unicode"
	override val description = i18n(Desc())
	override val syntax = buildSyntax(XOR) {
		flag("escape", Escape())
		flag("unescape", Unescape())
	}
	override val requiresKeystore = false
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		defaultNewline = false
		val firstChunk = readChunk()
		val stdin = firstChunk != null
		var string = firstChunk ?: prompt(">")
		var tail = ""
		var pendingHigh: Char? = null
		
		while (true) {
			if (hasArg("escape")) out(string.toUnicodeEscape())
			else if (hasArg("unescape")) {
				val text = tail + string
				tail = text.incompleteUnicodeTail()
				var head = text.substring(0, text.length - tail.length).unescapeUnicode(strict = false)
				if (pendingHigh != null) {
					head = pendingHigh + head
					pendingHigh = null
				}
				if (head.isNotEmpty() && head.last().isHighSurrogate()) {
					pendingHigh = head.last()
					head = head.dropLast(1)
				}
				out(head)
			} else unreachable()
			
			if (stdin) string = readChunk() ?: break
			else break
		}
		if (hasArg("unescape")) {
			if (pendingHigh != null) out(pendingHigh.toString())
			if (tail.isNotEmpty()) out(tail.unescapeUnicode(strict = false))
		}
		if (hasArg("escape")) ln()
		done()
	}
	
	private fun String.incompleteUnicodeTail(): String {
		var p = lastIndexOf('\\')
		while (p >= 0) {
			var n = 0
			var j = p - 1
			while (j >= 0 && this[j] == '\\') {
				n++; j--
			}
			if (n % 2 == 0) break
			p = j
		}
		if (p < 0 || this[p] != '\\') return ""
		val rest = substring(p)
		if (rest.length == 1) return rest
		if (rest.length in 2..5 && rest[1] == 'u' && rest.drop(2).all { it.digitToIntOrNull(16) != null }) return rest
		return ""
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
