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
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

enum class Style { GREEN, RED, YELLOW, BLUE, MAGENTA, CYAN, WHITE, BLACK, BOLD, DIM, ITALIC, UNDERLINE }

interface Console : I18nable {
	suspend fun out(text: String, vararg styles: Style, newline: Boolean = true)
	suspend fun err(text: String, vararg styles: Style, newline: Boolean = true)
	
	suspend fun ln()
	
	suspend fun status(vararg lines: String)
	suspend fun status(build: suspend StatusScope.() -> Unit)
	suspend fun clearStatus()
	
	suspend fun prompt(text: String): String
	suspend fun secret(text: String): String
	
	suspend fun <T> stream(flow: Flow<T>, render: suspend (T) -> Unit)
	
	suspend fun done(exitCode: Int = 0): Nothing
	
	suspend fun clear()
	suspend fun title(text: String)
	suspend fun enterAltScreen()
	suspend fun exitAltScreen()
}

interface StatusScope {
	fun line(text: String, vararg styles: Style)
}

class DoneException(val exitCode: Int) : CancellationException("Command done, exitCode=$exitCode")
