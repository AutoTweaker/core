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

package io.github.autotweaker.adapter.cli.console

import io.github.autotweaker.adapter.cli.OutputChannel
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.DoneException
import io.github.autotweaker.adapter.cli.commands.StyleBuilder
import io.github.autotweaker.api.*
import io.github.autotweaker.api.i18n.I18nDef
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

class ConsoleImpl(
	override val cwd: Path,
	private val isTty: Boolean,
	private val request: Request,
	private val stdin: ReceiveChannel<String>,
	private val readInput: suspend (echo: Boolean) -> String?,
	private val output: suspend (CmdOutput) -> Unit
) : Console {
	override var defaultNewline = true
	
	override suspend fun hasArg(name: String): Boolean =
		request.has(name)
	
	override suspend fun getValue(name: String): String =
		request.get(name) ?: error(i18n(ConsoleI18n.MissingValue(), name))
	
	override suspend fun getValueOrNull(name: String): String? =
		request.get(name)
	
	override suspend fun getPositional(): List<String> =
		request.positional
	
	override suspend fun getPositional(index: Int): String =
		request.positional.getOrElse(index) {
			error(i18n(ConsoleI18n.MissingPos(), index + 1))
		}
	
	override suspend fun getPositionalOrNull(index: Int): String? =
		request.positional.getOrNull(index)
	
	override suspend fun handleFlag(name: String, block: suspend () -> Unit) {
		if (request.has(name)) {
			block()
			done()
		}
	}
	
	override suspend fun handleValue(name: String, block: suspend (String) -> Unit) =
		request.get(name)?.let {
			block(it)
			done()
		}.discard()
	
	override suspend fun out(
		text: String,
		style: StyleBuilder.() -> Unit
	) = stdout(buildStyle(text, style))
	
	
	override suspend fun err(
		text: String,
		style: StyleBuilder.() -> Unit
	) = stderr(buildStyle(text, style))
	
	override suspend fun ln() = stdout("\n")
	
	private val stdinBuffer = StringBuilder()
	
	override suspend fun readLine(): String? {
		while (true) {
			val line = stdinBuffer.popLine()
			if (line != null) return line // 有一行
			val chunk = readChunk() // null == closed
				?: return if (stdinBuffer.isNotEmpty()) // 存在残缺块
					stdinBuffer.toString().also { stdinBuffer.clear() }
				else null // 已清空
			stdinBuffer.append(chunk) // 有数据，累加
		}
	}
	
	override suspend fun readChunk(): String? =
		stdin.receiveCatching().getOrNull()
	
	override suspend fun <T> stream(flow: Flow<T>, render: suspend (T) -> Unit) =
		flow.collect { render(it) }
	
	override suspend fun prompt(
		text: String,
		echo: Boolean,
		style: StyleBuilder.() -> Unit,
	): String = tryPrompt(text, echo, style) ?: error(ConsoleI18n.PromptError())
	
	override suspend fun promptOrNull(
		text: String, echo: Boolean, style: StyleBuilder.() -> Unit
	): String? = tryPrompt(text, echo, style).also { if (it == null) ln() }
	
	override suspend fun promptOrStdin(
		text: String, echo: Boolean, style: StyleBuilder.() -> Unit
	): String = tryPrompt(text, echo, style)
		?: readLine()?.also { if (echo) err(it) else err(MASK_CHAR * it.length) }
		?: error(ConsoleI18n.PromptOrStdinError())
	
	override suspend fun confirm(text: String, style: StyleBuilder.() -> Unit): Boolean =
		when (val result = promptOrStdin(text, true, style).trim().lowercase()) {
			"y", "t", "a", "1", "yes", "true", "approve" -> true
			"n", "f", "r", "0", "no", "false", "reject" -> false
			else -> error(ConsoleI18n.InvalidConfirm(), result)
		}
	
	override suspend fun title(text: String) {
		if (isTty) stdout(Ansi.title(text))
	}
	
	override suspend fun clear() {
		if (isTty) stdout(Ansi.clear())
	}
	
	override suspend fun altScreen(block: suspend () -> Unit) {
		if (isTty) {
			stdout(Ansi.ALT_SCREEN_ON + Ansi.HOME)
			block()
			stdout(Ansi.ALT_SCREEN_OFF)
		}
	}
	
	override suspend fun done(exitCode: Int): Nothing =
		throw DoneException(exitCode)
	
	override suspend fun error(text: String, style: StyleBuilder.() -> Unit): Nothing {
		stderr(buildStyle(text, style, red = true))
		done(1)
	}
	
	override suspend fun out(
		def: I18nDef,
		vararg args: Any?,
		style: StyleBuilder.() -> Unit
	) = out(i18n(def, *args), style)
	
	override suspend fun err(
		def: I18nDef,
		vararg args: Any?,
		style: StyleBuilder.() -> Unit
	) = err(i18n(def, *args), style)
	
	override suspend fun prompt(
		def: I18nDef,
		vararg args: Any?,
		echo: Boolean,
		style: StyleBuilder.() -> Unit
	): String = prompt(i18n(def, *args), echo, style)
	
	override suspend fun promptOrNull(
		def: I18nDef,
		vararg args: Any?,
		echo: Boolean,
		style: StyleBuilder.() -> Unit
	): String? = promptOrNull(i18n(def, *args), echo, style)
	
	override suspend fun promptOrStdin(
		def: I18nDef,
		vararg args: Any?,
		echo: Boolean,
		style: StyleBuilder.() -> Unit
	): String = promptOrStdin(i18n(def, *args), echo, style)
	
	override suspend fun confirm(
		def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit
	): Boolean = confirm(i18n(def, *args), style)
	
	override suspend fun title(def: I18nDef, vararg args: Any?) =
		title(i18n(def, *args))
	
	override suspend fun error(
		def: I18nDef,
		vararg args: Any?,
		style: StyleBuilder.() -> Unit
	): Nothing = error(i18n(def, *args), style)
	
	private suspend fun tryPrompt(
		text: String, echo: Boolean, style: StyleBuilder.() -> Unit
	): String? {
		stderr(buildStyle(text + SPACE, style, newline = false))
		return readInput(echo)
	}
	
	private fun buildStyle(
		text: String,
		style: StyleBuilder.() -> Unit,
		newline: Boolean? = null,
		red: Boolean = false
	): String {
		val style = StyleBuilderImpl(newline ?: defaultNewline).apply(style)
		if (red) style.red()
		val string = if (style.newline) text + '\n' else text
		return if (isTty) Ansi.styled(string, *style.codes.toTypedArray())
		else string
	}
	
	private suspend fun stdout(text: String) =
		output(CmdOutput(text, OutputChannel.STDOUT))
	
	private suspend fun stderr(text: String) =
		output(CmdOutput(text, OutputChannel.STDERR))
}

class StyleBuilderImpl(
	override var newline: Boolean
) : StyleBuilder {
	private val _codes = mutableSetOf<String>()
	val codes get() = _codes.toSet()
	
	private fun String.add() = _codes.add(this).discard()
	
	override fun black(background: Boolean) =
		if (background) Ansi.BG_BLACK.add()
		else Ansi.BLACK.add()
	
	override fun red(background: Boolean) =
		if (background) Ansi.BG_RED.add()
		else Ansi.RED.add()
	
	override fun green(background: Boolean) =
		if (background) Ansi.BG_GREEN.add()
		else Ansi.GREEN.add()
	
	override fun yellow(background: Boolean) =
		if (background) Ansi.BG_YELLOW.add()
		else Ansi.YELLOW.add()
	
	override fun blue(background: Boolean) =
		if (background) Ansi.BG_BLUE.add()
		else Ansi.BLUE.add()
	
	override fun magenta(background: Boolean) =
		if (background) Ansi.BG_MAGENTA.add()
		else Ansi.MAGENTA.add()
	
	override fun cyan(background: Boolean) =
		if (background) Ansi.BG_CYAN.add()
		else Ansi.CYAN.add()
	
	override fun white(background: Boolean) =
		if (background) Ansi.BG_WHITE.add()
		else Ansi.WHITE.add()
	
	override fun bold() = Ansi.BOLD.add()
	override fun dim() = Ansi.DIM.add()
	override fun italic() = Ansi.ITALIC.add()
	override fun underline() = Ansi.UNDERLINE.add()
}
