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

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import kotlinx.coroutines.flow.FlowCollector

sealed class CmdOutput {
	data class Data(
		val text: String, val channel: OutputChannel = OutputChannel.STDOUT, val newline: Boolean = true
	) : CmdOutput()
	
	data class Done(val exitCode: Int = 0) : CmdOutput()
	
	companion object : I18nable {
		suspend fun FlowCollector<CmdOutput>.emitI18n(
			def: I18nDef, vararg args: Any, error: Boolean = false
		) = emit(
			Data(
				i18n(def).format(*args),
				if (error) OutputChannel.STDERR else OutputChannel.STDOUT
			)
		)
		
		suspend fun FlowCollector<CmdOutput>.emitDone(exitCode: Int = 0) = emit(Done(exitCode))
	}
}
