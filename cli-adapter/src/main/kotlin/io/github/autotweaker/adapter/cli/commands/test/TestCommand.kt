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
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

@AutoService(Command::class)
class TestCommand : Command {
	override val name = "test"
	override val description = "Test command exercising all Console DSL APIs"
	override val syntax = buildSyntax {
		flag("skip-prompt", "跳过prompt") {
			aliases("sp")
			required = false
		}
		flag("skip-sleep", "是否跳过延迟") {
			aliases("ss")
			required = false
		}
	}.toAll()
	
	override suspend fun Console.render(request: Request) {
		val skipPrompt = request.has("skip-prompt")
		val skipSleep = request.has("skip-sleep")
		
		suspend fun sleep() = if (!skipSleep) delay(1.seconds) else Unit
		
		// out: 普通输出 + 样式 + 不换行
		out("[O] 这是普通输出")
		out("[O] 这是绿色文字", Style.GREEN)
		out("[O] 这是红色加粗文字", Style.RED, Style.BOLD)
		out("[O] 这是不换行文字", newline = false)
		out(" 接着写")
		
		// err: STDERR + 样式
		err("[E] 这是错误输出", Style.RED)
		err("[E] 这是加粗错误", Style.RED, Style.BOLD)
		
		// ln: 空行
		ln()
		
		// status: 单行状态栏
		status("[S] 阶段二：单行状态栏")
		sleep()
		
		// status: 多行
		status(
			"[S] 阶段三：进度 6 项",
			"[S] 阶段三：正在运行任务",
		)
		sleep()
		
		// status(build): 带样式的多行
		status {
			line("[S] 阶段四：阶段标签 ", Style.BOLD)
			line("[S] 阶段四：编译中", Style.GREEN)
			line("[S] 阶段四：错误数 0", Style.YELLOW)
		}
		sleep()
		
		// 状态栏 + out 交错
		status("[S] 阶段五：状态栏收缩为一行")
		stream(testFlow()) { item ->
			out("[O] 流式 $item", newline = false)
		}
		ln()
		status("[S] 阶段六：正在收尾")
		sleep()
		
		// stream: 流式输出
		stream(testFlow()) { item ->
			out("[O] 第二段流式 $item", newline = false)
			sleep()
		}
		
		sleep()
		ln()
		
		// clearStatus
		clearStatus()
		ln()
		
		// 状态栏 + prompt
		if (!skipPrompt) {
			status("[S] 阶段七：等待输入")
			val name = prompt("你的名字是？ ")
			status("[S] 阶段七：你好，$name！")
			sleep()
			val password = secret("请输入密码: ")
			out("[O] 密码长度: ${password.length}")
		}
		clearStatus()
		
		// title / clear / alt screen
		title("AutoTweaker — test")
		enterAltScreen()
		clear()
		out("Inside alternate screen!")
		out("[O] Something")
		sleep()
		out("[O] Something 2")
		sleep()
		exitAltScreen()
		
		// done
		done()
	}
	
	private fun testFlow(): Flow<String> = flow {
		for (i in 1..10) emit("$i ")
	}
}
