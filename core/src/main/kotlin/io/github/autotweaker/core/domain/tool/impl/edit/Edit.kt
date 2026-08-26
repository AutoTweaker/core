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
import io.github.autotweaker.api.generated.tool.args.EditArgs
import io.github.autotweaker.api.get
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonElement

@AutoService(CoreTool::class)
class Edit : CoreTool<EditArgs> {
	override suspend fun meta() = editMeta(
		EditMetaDescriptions(
			toolDescription = EditDesc.Tool().get(),
			functions = EditMetaDescriptions.Functions(
				single = EditMetaDescriptions.Functions.Single(
					filePath = ToolSettings.FilePathDesc().get(),
					lineFrom = EditDesc.SingleLineFrom().get(),
					lineTo = EditDesc.SingleLineTo().get(),
					oldString = EditDesc.SingleOldString().get(),
					unescapeOld = EditDesc.SingleUnescapeOldString().get(),
					newString = EditDesc.SingleNewString().get(),
					unescapeNew = EditDesc.SingleUnescapeNewString().get()
				) to EditDesc.Single().get(),
				batch = EditMetaDescriptions.Functions.Batch(
					files = EditDesc.BatchFiles().get(),
					regex = EditDesc.BatchRegex().get(),
					replaceWith = EditDesc.BatchReplaceWith().get(),
					unescapeConfig = EditDesc.BatchUnescapeConfig().get()
				) to EditDesc.Batch().get(),
				apply = EditMetaDescriptions.Functions.Apply(
					operationId = EditDesc.ApplyOperationId().get(),
				) to EditDesc.Apply().get()
			),
			types = EditMetaDescriptions.Types(
				unescapeConfig = EditMetaDescriptions.Types.UnescapeConfig(
					enableUnescape = EditDesc.UnescapeConfigEnable().get(),
					lenientMode = EditDesc.UnescapeConfigLenient().get()
				)
			)
		)
	)
	
	override suspend fun resolve(dependency: DependencyProvider, args: EditArgs): Tool.ResolveResult {
		TODO("暂未实现，请使用Bash来修改文件")
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		TODO("暂未实现，请使用Bash来修改文件")
	}
}
