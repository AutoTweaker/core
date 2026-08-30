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
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.generated.tool.args.EditArgs
import io.github.autotweaker.api.get
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.tool.text
import io.github.autotweaker.api.unescapeUnicode
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonElement

@AutoService(CoreTool::class)
class Edit : CoreTool<EditArgs>, Traceable {
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
		if (args !is EditArgs.Single) return Rejected("edit工具目前仅支持edit-single") {
			text("编辑文件失败，不支持的函数")
		}
		val fileSystem = dependency.get<FileSystemService>()
		
		val path = trace.catching { fileSystem.normalize(args.filePath) }
			.getOrElse {
				return Rejected(ToolSettings.PathErrorMessage().get()) {
					text("编辑文件失败，非法的路径：${args.filePath}")
				}
			}
		
		val displayPath = fileSystem.displayPath(path)
		
		val lineFrom = args.lineFrom ?: 1
		val lineTo = args.lineTo
		
		if (lineFrom < 1) return Rejected("line_from必须大于或等于1") {
			text("编辑文件失败，非法的请求参数")
		}
		if (lineTo != null && lineTo < lineFrom) return Rejected("line_to不能小于line_from") {
			text("编辑文件失败，非法的请求参数")
		}
		
		val oldString = let {
			val unescape = args.unescapeOld ?: return@let args.oldString
			if (args.unescapeOld?.enableUnescape == true) {
				val lenientMode = unescape.lenientMode ?: false
				args.oldString.map { it.unescapeUnicode(!lenientMode) }
			} else args.oldString
		}
		
		if (oldString.isEmpty()) return Rejected("old_string不能为空") {
			text("编辑文件失败，非法的请求参数")
		}
		
		oldString.singleOrNull()?.let {
			TODO()
		}
		TODO()
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		TODO("暂未实现，请使用Bash来修改文件")
	}
}
