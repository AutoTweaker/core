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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.tool.service.BashServiceImpl
import io.github.autotweaker.core.agent.tool.service.FileSystemServiceImpl
import io.github.autotweaker.core.agent.tool.service.SummarizeServiceImpl
import io.github.autotweaker.core.agent.tool.service.ToolCallHistoryImpl
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.impl.bash.BashService
import io.github.autotweaker.core.tool.impl.read.FileSystemService
import io.github.autotweaker.core.tool.impl.read.SummarizeService
import io.github.autotweaker.core.tool.impl.read.ToolCallHistory

internal fun buildToolProvider(env: AgentEnvironment): SimpleContainer {
	val container = SimpleContainer()
	val workspace = env.workspace
	val config = env.containerConfig
	container.register(
		FileSystemService::class,
		FileSystemServiceImpl(workspace.path, workspace.inContainer, config.workDir, config.workspaceHostPath),
	)
	container.register(
		SummarizeService::class,
		SummarizeServiceImpl(env.summarizeModel, env.currentFallbackModels),
	)
	container.register(BashService::class, BashServiceImpl(workspace.path, workspace.inContainer, config.workDir))
	container.register(ToolCallHistory::class, ToolCallHistoryImpl(env.context))
	return container
}
