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

@file:Suppress("unused")

package io.github.autotweaker.adapter.cli.commands

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.i18n.I18nDef
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

@ConsoleDsl
interface Console : I18nable {
	suspend fun hasArg(name: String): Boolean
	suspend fun getValue(name: String): String
	suspend fun getValueOrNull(name: String): String?
	suspend fun getPositional(): List<String>
	suspend fun getPositional(index: Int): String
	suspend fun getPositionalOrNull(index: Int): String?
	
	suspend fun handleFlag(name: String, block: suspend () -> Unit) // 末尾自动 done(0)
	suspend fun handleValue(name: String, block: suspend (String) -> Unit) // 末尾自动 done(0)
	
	suspend fun out(text: String, style: StyleBuilder.() -> Unit = {})
	suspend fun err(text: String, style: StyleBuilder.() -> Unit = {})
	suspend fun ln()
	
	suspend fun <T> stream(flow: Flow<T>, render: suspend (T) -> Unit)
	
	suspend fun prompt(text: String, style: StyleBuilder.() -> Unit = {}): String
	suspend fun secret(text: String, style: StyleBuilder.() -> Unit = {}): String
	suspend fun confirm(text: String, style: StyleBuilder.() -> Unit = {}): Boolean
	
	suspend fun title(text: String)
	suspend fun clear()
	suspend fun altScreen(block: suspend () -> Unit)
	
	suspend fun done(exitCode: Int = 0): Nothing
	suspend fun error(text: String, style: StyleBuilder.() -> Unit = {}): Nothing
	
	// i18n 区域
	suspend fun out(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {})
	suspend fun err(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {})
	suspend fun prompt(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {}): String
	suspend fun secret(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {}): String
	suspend fun confirm(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {}): Boolean
	suspend fun title(def: I18nDef, vararg args: Any?)
	suspend fun error(def: I18nDef, vararg args: Any?, style: StyleBuilder.() -> Unit = {}): Nothing
}

@ConsoleDsl
interface StyleBuilder {
	var newline: Boolean // 默认 true
	
	fun black(background: Boolean = false)
	fun red(background: Boolean = false)
	fun green(background: Boolean = false)
	fun yellow(background: Boolean = false)
	fun blue(background: Boolean = false)
	fun magenta(background: Boolean = false)
	fun cyan(background: Boolean = false)
	fun white(background: Boolean = false)
	
	fun bold()
	fun dim()
	fun italic()
	fun underline()
}

class DoneException(val exitCode: Int) : // 使用异常作为常规业务流程，允许 try-catch
	CancellationException("Command done, exitCode=$exitCode") {
	override fun fillInStackTrace(): Throwable {
		return this
	}
}

@DslMarker
annotation class ConsoleDsl
