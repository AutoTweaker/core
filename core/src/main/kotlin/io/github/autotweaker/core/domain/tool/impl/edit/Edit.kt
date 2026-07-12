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

package io.github.autotweaker.core.domain.tool.impl.edit

import com.google.auto.service.AutoService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.tool.args.edit.EditArgs
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import kotlinx.coroutines.channels.Channel

@AutoService(CoreTool::class)
class Edit : CoreTool<EditArgs> {
	override val meta = EditMeta.meta
	
	override suspend fun coreExec(
		container: DependencyProvider,
		args: EditArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		TODO("Not yet implemented")
	}
}
