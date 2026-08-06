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
import io.github.autotweaker.adapter.cli.commands.Style
import io.github.autotweaker.adapter.cli.syntax.Request
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@AutoService(Command::class)
class ChatCommand : Command {
	override val name = "chat"
	override val description = "test chat"
	override val syntax = Syntax.EMPTY
	
	private lateinit var core: CoreAPI
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override suspend fun Console.render(request: Request) {
		prompt("输入请求:")
		status("正在处理中")
		var count = 0
		stream(outputFlow()) {
			out(it.toString(), Style.BLUE, newline = false)
			if (count++ % 100 == 0) status("正在处理中", "当前输出数量$count")
		}
		status("处理完毕")
		delay(2.seconds)
		prompt("输入请求:")
		status("正在处理中")
		count = 0
		stream(outputFlow()) {
			out(it.toString(), Style.BLUE, newline = false)
			if (count++ % 100 == 0) status("正在处理中", "当前输出数量$count")
		}
		status("处理完毕")
		done()
	}
	
	private fun outputFlow(): Flow<Char> = flow {
		getSystemPrompt().forEach {
			emit(it)
			delay(10.milliseconds)
		}
	}
	
	private fun getSystemPrompt() = (core.config.getAllSettings()
		.find { it.id == "io.github.autotweaker.core.domain.session.SessionManager.SystemPrompt" }
		?.value as SettingValue.ValString).value
}
