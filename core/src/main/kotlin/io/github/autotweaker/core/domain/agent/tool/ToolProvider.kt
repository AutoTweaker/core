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
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.tool.service.BashServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.FileSystemServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.SummarizeServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.ToolCallHistoryImpl
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlin.time.Clock

internal object ToolProvider {
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
			SummarizeServiceImpl(
				env.summarizeModel, env.currentFallbackModels,
				onUsage = { snapshot ->
					env.emitOutput(
						AgentOutput.UsageConsumed(Clock.System.now(), snapshot.usage, snapshot.model)
					)
				},
			),
		)
		container.register(BashService::class, BashServiceImpl(workspace.path, workspace.inContainer, config.workDir))
		container.register(ToolCallHistory::class, ToolCallHistoryImpl(env.context.value))
		return container
	}
}
