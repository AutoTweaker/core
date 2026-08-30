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
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.compact.SummaryService
import io.github.autotweaker.core.domain.agent.tool.service.*
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.port.TemporaryStorage
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.ServiceContainer
import io.github.autotweaker.core.domain.tool.port.*
import java.nio.file.Path

class ToolProvider(
	private val shellExecutor: ShellExecutor,
	private val rawFileSystem: RawFileSystem,
	private val pathResolver: PathResolver,
	private val temporaryStorage: TemporaryStorage,
	private val summaryService: SummaryService,
) {
	fun build(
		workspace: () -> Path,
		model: AgentModel,
		context: RuntimeContext,
		onOutput: (RuntimeOutput) -> Unit,
		truncation: TruncationService,
	): DependencyProvider = ServiceContainer()
		.register<FileSystemService>(
			FileSystemServiceImpl(rawFileSystem, pathResolver, workspace)
		).register<SummarizeService>(
			SummarizeServiceImpl(model, summaryService, onOutput)
		).register<BashService>(
			BashServiceImpl(shellExecutor, pathResolver, workspace)
		).register<ToolCallHistory>(
			ToolCallHistoryImpl(context)
		).register<TruncationService>(
			truncation
		).register<ClipboardService>(
			ClipboardServiceImpl(temporaryStorage, pathResolver, workspace)
		)
}
