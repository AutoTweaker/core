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
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.unifiedDiff
import io.github.autotweaker.api.generated.tool.args.EditArgs
import io.github.autotweaker.api.generated.tool.args.UnescapeConfig
import io.github.autotweaker.api.tool.Ready
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolSuccess
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.tool.diff
import io.github.autotweaker.api.types.tool.edit.EditRequest
import io.github.autotweaker.api.types.tool.text
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.impl.write.WriteMessage
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@AutoService(CoreTool::class)
class Edit : CoreTool<EditArgs>, Traceable {
	override suspend fun meta() = editMeta(
		EditMetaDescriptions(
			toolDescription = EditDesc.Tool().get(),
			functions = EditMetaDescriptions.Functions(
				single = EditMetaDescriptions.Functions.Single(
					filePath = ToolSettings.FilePathDesc().get(),
					sha256 = EditDesc.SingleSha256().get(),
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
		)
	)
	
	private val requestSerializer = EditRequest.serializer()
	
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
		
		val sha256 = trace.catching { Sha256(args.sha256) }
			.getOrElse { e ->
				return Rejected(WriteMessage.InvalidHash().format(e.message)) {
					text("编辑文件 $displayPath 失败，非法的请求参数")
				}
			}
		
		val fileContent = trace.catching { fileSystem.read(path) }
			.getOrElse { e ->
				return Rejected("读取目标文件时出错：${e.message()}") {
					text("编辑文件 $displayPath 失败，无法读取目标文件")
				}
			}
		
		if (fileContent.sha256 != sha256)
			return Rejected("编辑文件失败，SHA256不匹配，文件已被外部更新，请重新读取文件") {
				text("编辑文件 $displayPath 失败，文件已被外部更改")
			}
		
		val lineFrom = args.lineFrom ?: 1
		val lineTo = args.lineTo
		
		if (lineFrom < 1) return Rejected("line_from必须大于或等于1") {
			text("编辑文件 $displayPath 失败，非法的请求参数")
		}
		if (lineTo != null && lineTo < lineFrom) return Rejected("line_to不能小于line_from") {
			text("编辑文件 $displayPath 失败，非法的请求参数")
		}
		
		val oldString = trace.catching { args.oldString.unescape(args.unescapeOld) }
			.getOrElse { e ->
				return Rejected("old_string中包含非法或未知的转义序列：${e.message}") {
					text("编辑文件 $displayPath 失败，非法的转义")
				}
			}
		
		if (oldString.isEmpty()) return Rejected("old_string不能为空") {
			text("编辑文件 $displayPath 失败，非法的请求参数")
		}
		
		val newString = trace.catching { args.newString.unescape(args.unescapeNew) }
			.getOrElse { e ->
				return Rejected("new_string中包含非法或未知的转义序列：${e.message}") {
					text("编辑文件 $displayPath 失败，非法的转义")
				}
			}
		
		val oldContent = fileContent.content
		var lineStart = 0
		repeat(lineFrom - 1) {
			val next = oldContent.indexOf('\n', lineStart)
			lineStart = if (next == -1) oldContent.length else next + 1
		}
		var lineEnd = oldContent.length
		if (lineTo != null) {
			var cursor = lineStart
			repeat(lineTo - lineFrom) {
				val next = oldContent.indexOf('\n', cursor)
				cursor = if (next == -1) oldContent.length else next + 1
			}
			val next = oldContent.indexOf('\n', cursor)
			if (next != -1) lineEnd = next
		}
		
		val rangeContent = oldContent.substring(lineStart, lineEnd)
		val matchIndex = rangeContent.indexOf(oldString)
		
		if (matchIndex == -1) return Rejected("指定的范围内没有old_string的匹配项。请重新读取文件确认当前状态符合预期，并确保提供的字符精确") {
			text("编辑文件 $displayPath 失败，无匹配内容")
		}
		if (matchIndex != rangeContent.lastIndexOf(oldString))
			return Rejected("指定的范围内存在多处old_string的匹配项，请尝试缩小行区间或在old_string中提供更多上下文") {
				text("编辑文件 $displayPath 失败，匹配项不唯一")
			}
		
		val newContent = oldContent.replaceRange(
			lineStart + matchIndex,
			lineStart + matchIndex + oldString.length,
			newString
		)
		
		return Ready(
			requestSerializer,
			EditRequest(
				path, displayPath, oldContent to fileContent.sha256, newContent
			),
			request = { reason ->
				text("请求编辑 $displayPath（${reason}）")
				diff(path, oldContent, newContent)
			},
			executing = {
				text("正在编辑 $displayPath")
			},
			cancelled = {
				text("编辑 $displayPath 被取消")
			},
			rejected = { reason ->
				if (reason == null) text("编辑 $displayPath 被拒绝")
				else text("编辑 $displayPath 被拒绝：$reason")
				diff(path, oldContent, newContent)
			},
			failed = { e ->
				text("编辑 $displayPath 失败：${e.message()}")
			},
			timeout = { elapsed ->
				text("编辑 $displayPath 超时：$elapsed")
			}
		)
	}
	
	private fun String.unescape(mode: UnescapeConfig?): String {
		val unescapeMode = mode ?: UnescapeConfig.DISABLE
		return when (unescapeMode) {
			UnescapeConfig.DISABLE -> this
			UnescapeConfig.DEFAULT -> unescapeUnicode(strict = true)
			UnescapeConfig.LENIENT_MODE -> unescapeUnicode(strict = false)
		}
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val request = Json.decodeFromJsonElement(requestSerializer, request)
		val fileSystem = dependency.get<FileSystemService>()
		val oldContent = request.expected.first
		val sha256 = request.expected.second
		fileSystem.update(request.path, sha256, request.newContent)
		return "已更新文件 ${request.displayPath}：\n${
			unifiedDiff(
				oldContent,
				request.newContent
			) ?: "UNCHANGED"
		}".toolSuccess {
			text("编辑了 ${request.displayPath}")
			diff(
				request.path,
				oldContent,
				request.newContent
			)
		}
	}
}
