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

package io.github.autotweaker.core.application

import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.infrastructure.tool.ContainerShellExecutor
import io.github.autotweaker.core.infrastructure.tool.LocalShellExecutor
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

class ShellRouter : ShellExecutor {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val local = LocalShellExecutor()
	private val container = ContainerShellExecutor()

	override fun exec(arg: ShellExec): Flow<ShellEvent> {
		val target = if (arg.container) "container" else "local"
		logger.debug("Routed shell command  target={}  command={}", target, arg.command)
		return if (arg.container)
			container.exec(arg.command, arg.directory, arg.environment, arg.timeout)
		else
			local.exec(arg.command, arg.directory, arg.environment, arg.timeout)
	}
}
