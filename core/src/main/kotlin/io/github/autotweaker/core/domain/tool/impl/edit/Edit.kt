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
import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.tool.args.edit.EditArgs
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@AutoService(CoreTool::class)
class Edit : CoreTool<EditArgs>, Settable {
	override val name = "edit"
	override val argsSerializer = EditArgs.serializer()
	override val description = setting(EditPrompt.EditDesc())
	
	override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
		EditArgs.Run::class to "",
		EditArgs.Apply::class to "",
		EditArgs.GetClip::class to ""
	)
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf()
	
	override suspend fun coreExec(
		container: DependencyProvider,
		args: EditArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		TODO("Not yet implemented")
	}
}
