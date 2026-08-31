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
				file = EditMetaDescriptions.Functions.File(
					filePath = ToolSettings.FilePathDesc().get(),
					sha256 = EditDesc.Sha256().get(),
					lineFrom = EditDesc.LineFrom().get(),
					lineTo = EditDesc.LineTo().get(),
					oldString = EditDesc.OldString().get(),
					unescapeOld = EditDesc.UnescapeOldString().get(),
					newString = EditDesc.NewString().get(),
					unescapeNew = EditDesc.UnescapeNewString().get()
				) to EditDesc.Single().get()
			),
		)
	)
	
	private val requestSerializer = EditRequest.serializer()
	
	override suspend fun resolve(dependency: DependencyProvider, args: EditArgs): Tool.ResolveResult {
		val request = args as EditArgs.File
		val fileSystem = dependency.get<FileSystemService>()
		
		val path = trace.catching { fileSystem.normalize(request.filePath) }
			.getOrElse {
				return Rejected(ToolSettings.PathErrorMessage().get()) {
					text(i18n(EditI18n.InvalidPath(), request.filePath))
				}
			}
		
		val displayPath = fileSystem.displayPath(path)
		
		val sha256 = trace.catching { Sha256(request.sha256) }
			.getOrElse { e ->
				return Rejected(EditMessage.InvalidHash().format(e.message)) {
					text(i18n(EditI18n.InvalidArg(), displayPath))
				}
			}
		
		val fileContent = trace.catching { fileSystem.read(path) }
			.getOrElse { e ->
				return Rejected(EditMessage.ReadFailed().format(e.message())) {
					text(i18n(EditI18n.ReadFailed(), displayPath))
				}
			}
		
		if (fileContent.sha256 != sha256)
			return Rejected(EditMessage.HashMismatch().get()) {
				text(i18n(EditI18n.UpdateFailedChanged(), displayPath))
			}
		
		val lineFrom = request.lineFrom ?: 1
		val lineTo = request.lineTo
		
		if (lineFrom < 1) return Rejected(EditMessage.LineFromInvalid().get()) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		if (lineTo != null && lineTo < lineFrom) return Rejected(EditMessage.LineToInvalid().get()) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		
		val oldString = trace.catching { request.oldString.unescape(request.unescapeOld) }
			.getOrElse { e ->
				return Rejected(EditMessage.InvalidEscape().format("old_string", e.message)) {
					text(i18n(EditI18n.InvalidEscape(), displayPath))
				}
			}
		
		if (oldString.isEmpty()) return Rejected(EditMessage.OldStringEmpty().get()) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		
		val newString = trace.catching { request.newString.unescape(request.unescapeNew) }
			.getOrElse { e ->
				return Rejected(EditMessage.InvalidEscape().format("new_string", e.message)) {
					text(i18n(EditI18n.InvalidEscape(), displayPath))
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
		
		if (matchIndex == -1) return Rejected(EditMessage.NoMatch().get()) {
			text(i18n(EditI18n.NoMatch(), displayPath))
		}
		if (matchIndex != rangeContent.lastIndexOf(oldString))
			return Rejected(EditMessage.NotUnique().get()) {
				text(i18n(EditI18n.NotUnique(), displayPath))
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
				text(i18n(EditI18n.Request(), displayPath, reason))
				diff(path, oldContent, newContent)
			},
			executing = {
				text(i18n(EditI18n.Executing(), displayPath))
			},
			cancelled = {
				text(i18n(EditI18n.Cancelled(), displayPath))
			},
			rejected = { reason ->
				if (reason == null) text(i18n(EditI18n.Rejected(), displayPath))
				else text(i18n(EditI18n.RejectedWithReason(), displayPath, reason))
				diff(path, oldContent, newContent)
			},
			failed = { e ->
				text(i18n(EditI18n.Failed(), displayPath, e.message()))
			},
			timeout = { elapsed ->
				text(i18n(EditI18n.Timeout(), displayPath, elapsed))
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
		return EditMessage.Updated().format(
			request.displayPath,
			unifiedDiff(
				oldContent,
				request.newContent
			) ?: EditMessage.Unchanged().get()
		).toolSuccess {
			text(i18n(EditI18n.Updated(), request.displayPath))
			diff(
				request.path,
				oldContent,
				request.newContent
			)
		}
	}
}
