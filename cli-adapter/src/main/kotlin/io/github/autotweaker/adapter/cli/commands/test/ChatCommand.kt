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

package io.github.autotweaker.adapter.cli.commands.test

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.randomStyle
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

@AutoService(Command::class)
class ChatCommand : Command {
	override val name = "chat"
	override val description = "test chat"
	override val syntax = Syntax.EMPTY
	
	private lateinit var core: CoreAPI
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		this@ChatCommand.core = core
		prompt("输入请求:")
		stream(outputFlow()) {
			out(it.toString()) {
				newline = false
				randomStyle()
			}
		}
		altScreen {
			stream(outputFlow()) {
				out(it.toString()) {
					newline = false
					blue()
				}
			}
		}
		done()
	}
	
	private fun outputFlow(): Flow<Char> = flow {
		getSystemPrompt().forEach {
			emit(it)
			delay(1.milliseconds)
		}
	}
	
	private fun getSystemPrompt() = (core.config.getAllSettings()
		.find { it.id == "io.github.autotweaker.core.domain.session.SessionManager.SystemPrompt" }
		?.value as SettingValue.ValString).value
}
