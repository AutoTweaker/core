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

package io.github.autotweaker.core.domain.tool

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.coroutines.channels.Channel

interface CoreTool<Args : ToolArgs> : Tool<Args> {
	suspend fun init(secretStore: SecretStore) {}
	suspend fun coreExec(
		container: DependencyProvider,
		args: Args,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput
	
	override suspend fun execute(args: Args, outputChannel: Channel<Tool.RuntimeOutput>?): Tool.ToolOutput =
		throw UnsupportedOperationException("Use coreExec")
}
