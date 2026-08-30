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

package io.github.autotweaker.core.domain.tool.impl.write

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.unifiedDiff
import io.github.autotweaker.api.generated.tool.args.WriteArgs
import io.github.autotweaker.api.tool.Ready
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolSuccess
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.api.types.tool.diff
import io.github.autotweaker.api.types.tool.text
import io.github.autotweaker.api.types.tool.write.WriteRequest
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@AutoService(CoreTool::class)
class Write : CoreTool<WriteArgs>, Traceable {
	override suspend fun meta() = writeMeta(
		WriteMetaDescriptions(
			toolDescription = "创建一个新文件，或覆写一个已有文件。支持Unicode转义，不要使用bash来创建文件，而是激活此工具来写入文件，即使要写入特殊字符。优先使用edit来更新文件的部分片段",
			functions = WriteMetaDescriptions.Functions(
				file = WriteMetaDescriptions.Functions.File(
					filePath = ToolSettings.FilePathDesc().get(),
					sha256 = "如果目标文件已存在，请通过read工具读取文件的完整内容，并提供read工具返回的文件当前SHA256，这能够避免意外覆盖来自用户或外部程序的文件更新",
					content = "要写入到文件的新内容，如果启用unescape_unicode，可以包含若干Unicode转义序列，普通字符会按原样解析",
					unescapeUnicode = "是否对content中的Unicode转义序列进行解码，默认false，仅支持Unicode转义以及反斜杠转义，例如\\u0055将被解析为'U'，\\\\u0055将被解析为'\\u0055'",
					lenientUnescape = "若启用unescape_unicode，将原样保留content中不合法或不支持的转义，通常不应当启用，仅在要写入到内容确实包含大量字面反斜杠或Json转义（字面），同时又必须使用Unicode转义时启用，默认false"
				) to "创建一个新文件，或覆写已有文件，你需要确保提前通过read工具读取目标文件。\n支持unicode转义，不支持\\n等json转义，需要通过unescape_unicode显式启用。\n你应该优先使用edit来更新文件的部分片段。\n始终避免在工作区中创建临时文件或任务报告类文件，请在'/tmp/$APP_NAME_LOWERCASE'下创建这类文件。\nfile_path的父目录若不存在会自动创建，如果你已经确认了要写入的位置，无需提前创建目录或检查父目录的存在性。"
			)
		)
	)
	
	private val requestSerializer = WriteRequest.serializer()
	
	override suspend fun resolve(dependency: DependencyProvider, args: WriteArgs): Tool.ResolveResult {
		val request = args as WriteArgs.File
		val fileSystem = dependency.get<FileSystemService>()
		
		val path = trace.catching { fileSystem.normalize(request.filePath) }
			.getOrElse {
				return Rejected(ToolSettings.PathErrorMessage().get()) {
					text("创建或覆盖文件失败，非法的路径：${request.filePath}")
				}
			}
		
		val displayPath = fileSystem.displayPath(path)
		val sha256 = args.sha256?.let {
			trace.catching { Sha256(it) }
				.getOrElse { e ->
					return Rejected("无效的哈希：${e.message}") {
						text("覆盖文件 $displayPath 失败，非法的请求参数")
					}
				}
		}
		val fileContent = trace.catching {
			fileSystem.read(path)
		}.rethrow<PathOutsideWorkspaceException>().getOrNull()
		if (fileContent != null && sha256 == null) return Rejected(
			"文件 $displayPath 已存在，如需覆写请使用read工具读取后提供sha256"
		) {
			text("创建文件 $displayPath 失败，文件已存在")
		}
		if (sha256 != null && fileContent != null && sha256 != fileContent.sha256)
			return Rejected("覆盖文件 $displayPath 失败，SHA256不匹配，文件已被外部更新，请重新读取文件") {
				text("覆盖文件 $displayPath 失败，文件已被外部更改")
			}
		val newContent = let {
			val unescape = request.unescapeUnicode ?: false
			val lenient = request.lenientUnescape ?: false
			if (!unescape) request.content
			else trace.catching {
				request.content.unescapeUnicode(!lenient)
			}.getOrElse { e ->
				return Rejected("未知或不合法的转义：${e.message}") {
					text("创建或覆盖文件 $displayPath 失败，非法的转义")
				}
			}
		}
		val write = if (sha256 == null) "创建" else "更新"
		return Ready(
			requestSerializer,
			WriteRequest(path, displayPath, fileContent?.let { it.content to it.sha256 }, newContent),
			request = { reason ->
				text("请求$write $displayPath（$reason）")
				diff(path, fileContent?.content, newContent)
			},
			executing = {
				text("正在$write $displayPath")
			},
			cancelled = {
				text("$write $displayPath 被取消")
			},
			rejected = { reason ->
				if (reason == null)
					text("$write $displayPath 被拒绝")
				else text("$write $displayPath 被拒绝：$reason")
				diff(path, fileContent?.content, newContent)
			},
			failed = { e ->
				text("$write $displayPath 失败：${e.message()}")
			},
			timeout = { elapsed ->
				text("$write $displayPath 超时：$elapsed")
			}
		)
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val request = Json.decodeFromJsonElement(requestSerializer, request)
		val fileSystem = dependency.get<FileSystemService>()
		val sha256 = request.expected?.second
		if (sha256 == null) {
			fileSystem.create(request.path, request.content)
			return "创建了文件 ${request.displayPath}：\n${
				unifiedDiff(
					null,
					request.content
				) ?: "UNCHANGED"
			}".toolSuccess {
				text("创建了 ${request.displayPath}")
				diff(request.path, null, request.content)
			}
		} else {
			fileSystem.update(request.path, sha256, request.content)
			return "覆盖了文件 ${request.displayPath}：\n${
				unifiedDiff(
					request.expected?.first,
					request.content
				) ?: "UNCHANGED"
			}".toolSuccess {
				text("覆盖了 ${request.displayPath}")
				diff(request.path, request.expected?.first, request.content)
			}
		}
	}
}
