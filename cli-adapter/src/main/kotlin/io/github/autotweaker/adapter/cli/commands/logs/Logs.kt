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

package io.github.autotweaker.adapter.cli.commands.logs

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.StyleBuilder
import io.github.autotweaker.adapter.cli.syntax.ALL
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.SPACE
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.log.LogLevel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@AutoService(Command::class)
class Logs : Command {
	override val name = "logs"
	override val description = i18n(Desc())
	override val syntax = buildSyntax(ALL) {
		value("filter", Filter()) { required = false }
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		defaultNewline = false
		handleValue("filter") { logger ->
			core.log.flow.collect {
				if (it.logger.startsWith(logger))
					it.printLog()
			}
		}
		core.log.flow.collect {
			it.printLog()
		}
	}
	
	context(c: Console)
	private suspend fun LogEvent<ExceptionInfo.Live>.printLog() {
		with(c) {
			out(timestamp.toTimestamp())
			space()
			out("[${thread}]") { magenta() }
			space()
			out(level.toString()) { level.toColor() }
			space()
			out(abbreviateLogger(logger)) { blue() }
			out(" - ")
			out(message) { level.toColor() }
			ln()
			exception?.throwable?.stackTraceToString()?.let { text ->
				out(text.trimEnd()) { red() }
				ln()
			}
		}
	}
	
	context(s: StyleBuilder)
	private fun LogLevel.toColor() = when (this) {
		LogLevel.TRACE -> s.white()
		LogLevel.DEBUG -> s.green()
		LogLevel.INFO -> s.cyan()
		LogLevel.WARN -> s.yellow()
		LogLevel.ERROR -> s.red()
	}
	
	private val timeFormat = LocalDateTime.Format {
		hour()
		char(':')
		minute()
		char(':')
		second()
		char('.')
		secondFraction(3)
	}
	
	fun Instant.toTimestamp(): String =
		timeFormat.format(toLocalDateTime(TimeZone.currentSystemDefault()))
	
	fun abbreviateLogger(name: String): String {
		if (name.length <= 36) return name
		val parts = name.split('.')
		if (parts.size < 2) return name
		val abbr = parts.toMutableList()
		for (i in 0 until abbr.size - 1) {
			if (abbr.joinToString(".").length <= 36) break
			abbr[i] = abbr[i].first().toString()
		}
		return abbr.joinToString(".")
	}
	
	private suspend fun Console.space() {
		out(SPACE.toString()) { newline = false }
	}
	
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("显示程序日志"),
	)
	
	@AutoService(I18nDef::class)
	class Filter : I18nBase(
		zh("仅显示属于指定包及其子包的日志"),
	)
}
