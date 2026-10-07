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
import io.github.autotweaker.api.tool.buildOutput
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
import kotlinx.serialization.json.decodeFromJsonElement

@AutoService(CoreTool::class)
class Write : CoreTool<WriteArgs>, Traceable {
	override suspend fun meta() = writeMeta(
		WriteMetaDescriptions(
			toolDescription = WriteDesc.Tool().get(),
			functions = WriteMetaDescriptions.Functions(
				default = WriteMetaDescriptions.Functions.Default(
					filePath = ToolSettings.FilePathDesc().get(),
					sha256 = WriteDesc.Sha256().get(),
					content = WriteDesc.Content().get(),
					unescapeUnicode = WriteDesc.UnescapeUnicode().get(),
					lenientUnescape = WriteDesc.LenientUnescape().get()
				) to WriteDesc.Function().get()
			)
		)
	)
	
	override suspend fun resolve(dependency: DependencyProvider, args: WriteArgs): Tool.ResolveResult {
		val request = args as WriteArgs.Default
		val fileSystem = dependency.get<FileSystemService>()
		
		val path = trace.catching { fileSystem.normalize(request.filePath) }
			.getOrElse {
				return Rejected(ToolSettings.PathErrorMessage().get()) {
					text(WriteI18n.InvalidPath(), request.filePath)
				}
			}
		
		val displayPath = fileSystem.displayPath(path)
		val shortHash = args.sha256
		if (shortHash != null && shortHash.length < 8)
			return Rejected(ToolSettings.InvalidHash().format(shortHash, shortHash.length)) {
				text(WriteI18n.InvalidHashArg(), displayPath)
			}
		val fileContent = trace.catching {
			fileSystem.read(path)
		}.rethrow<PathOutsideWorkspaceException>().getOrNull()
		if (fileContent != null && shortHash == null) return Rejected(
			WriteMessage.FileExists().format(displayPath)
		) {
			text(WriteI18n.CreateFailedExists(), displayPath)
		}
		if (shortHash != null && fileContent != null && !fileContent.sha256.toString()
				.startsWith(shortHash, ignoreCase = true)
		) return Rejected(buildString {
			append(WriteMessage.HashMismatch().format(displayPath, shortHash))
			if (shortHash.length > 8) {
				appendLine()
				append(ToolSettings.UseShortHash().format(shortHash.length))
			}
		}) {
			text(WriteI18n.UpdateFailedChanged(), displayPath)
		}
		val newContent = let {
			val unescape = request.unescapeUnicode ?: false
			val lenient = request.lenientUnescape ?: false
			if (!unescape) request.content
			else trace.catching {
				request.content.unescapeUnicode(!lenient)
			}.getOrElse { e ->
				return Rejected(WriteMessage.InvalidEscape().format(e.message)) {
					text(WriteI18n.InvalidEscape(), displayPath)
				}
			}
		}
		val oldContent = fileContent?.let {
			if (it.truncated) null else it.content
		}
		val write = i18n(if (shortHash == null) WriteI18n.Create() else WriteI18n.Update())
		return Ready(
			result = WriteRequest(
				path = path,
				displayPath = displayPath,
				expected = fileContent?.let { oldContent to it.sha256 },
				content = newContent
			),
			request = { reason ->
				text(WriteI18n.Request(), write, displayPath, reason)
				diff(path, oldContent, newContent)
			},
			executing = {
				text(WriteI18n.Executing(), write, displayPath)
			},
			cancelled = {
				text(WriteI18n.Cancelled(), write, displayPath)
			},
			rejected = { reason ->
				if (reason == null) text(WriteI18n.Rejected(), write, displayPath)
				else text(WriteI18n.RejectedWithReason(), write, displayPath, reason)
				diff(path, oldContent, newContent)
			},
			failed = { e ->
				text(WriteI18n.Failed(), write, displayPath, e.message())
			},
			timeout = { elapsed ->
				text(WriteI18n.Timeout(), write, displayPath, elapsed)
			}
		)
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val request = Json.decodeFromJsonElement<WriteRequest>(request)
		val fileSystem = dependency.get<FileSystemService>()
		val sha256 = request.expected?.second
		if (sha256 == null) {
			val result = fileSystem.create(request.path, request.content)
			return buildOutput {
				success = true
				content(WriteMessage.Created().format(request.displayPath, result))
				text(WriteI18n.Created(), request.displayPath)
				diff(request.path, null, request.content)
			}
		} else {
			val updated = fileSystem.update(request.path, sha256, request.content)
			val oldContent = request.expected?.first
			return buildOutput {
				success = true
				val diff = if (oldContent != null) {
					unifiedDiff(oldContent, request.content) ?: WriteMessage.Unchanged().get()
				} else WriteMessage.TooLarge().get()
				content(WriteMessage.Updated().format(request.displayPath, updated, diff))
				text(WriteI18n.Updated(), request.displayPath)
				diff(request.path, oldContent, request.content)
			}
		}
	}
}
