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

import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.tool.service.BashServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.FileSystemServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.SummarizeServiceImpl
import io.github.autotweaker.core.domain.agent.tool.service.ToolCallHistoryImpl
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.ServiceContainer
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory

object ToolProvider {
	private lateinit var shellExecutor: ShellExecutor
	private lateinit var rawFileSystem: RawFileSystem
	private lateinit var pathResolver: PathResolver
	
	fun init(shellExecutor: ShellExecutor, rawFileSystem: RawFileSystem, pathResolver: PathResolver) {
		this.shellExecutor = shellExecutor
		this.rawFileSystem = rawFileSystem
		this.pathResolver = pathResolver
	}
	
	fun buildToolProvider(
		workspace: WorkspaceMeta,
		model: AgentModel,
		context: AgentContext,
		onOutput: (AgentOutput) -> Unit,
	): DependencyProvider = ServiceContainer()
		.register(
			FileSystemService::class,
			FileSystemServiceImpl(rawFileSystem, pathResolver, workspace)
		)
		.register(
			SummarizeService::class,
			SummarizeServiceImpl(model, onOutput)
		)
		.register(
			BashService::class,
			BashServiceImpl(shellExecutor, pathResolver, workspace)
		)
		.register(
			ToolCallHistory::class,
			ToolCallHistoryImpl(context)
		)
}
