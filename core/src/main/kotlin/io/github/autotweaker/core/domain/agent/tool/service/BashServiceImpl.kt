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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.tool.port.BashService
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import kotlin.time.Duration

internal class BashServiceImpl(
	private val executor: ShellExecutor,
	private val env: AgentEnvironment,
) : BashService {
	override fun run(command: String, timeout: Duration, env: Map<String, String>): Flow<ShellEvent> {
		val workDir: Path = this.env.workspace.path.normalize()
		val inContainer = this.env.containerConfig.isContainerPath(workDir)
		return executor.exec(ShellExec(command, workDir, inContainer, env, timeout))
	}
}
