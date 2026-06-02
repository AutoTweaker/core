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

package io.github.autotweaker.core.domain.agent.tool

import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.tool.service.BashServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.FileSystemServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.SummarizeServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.ToolCallHistoryImpl
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory

object ToolProvider {
	@Volatile
	private lateinit var shellExecutor: ShellExecutor
	
	@Volatile
	private lateinit var rawFileSystem: RawFileSystem
	
	fun init(shellExecutor: ShellExecutor, rawFileSystem: RawFileSystem) {
		this.shellExecutor = shellExecutor
		this.rawFileSystem = rawFileSystem
	}
	
	fun buildToolProvider(env: AgentEnvironment): SimpleContainer {
		val container = SimpleContainer()
		container.register(FileSystemService::class, FileSystemServiceImpl(rawFileSystem, env))
		container.register(SummarizeService::class, SummarizeServiceImpl(env))
		container.register(BashService::class, BashServiceImpl(shellExecutor, env))
		container.register(ToolCallHistory::class, ToolCallHistoryImpl(env))
		return container
	}
}
