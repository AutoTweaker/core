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
				default = EditMetaDescriptions.Functions.Default(
					filePath = ToolSettings.FilePathDesc().get(),
					sha256 = EditDesc.Sha256().get(),
					edits = EditDesc.Edits().get()
				) to EditDesc.Default().get()
			),
			types = EditMetaDescriptions.Types(
				replacement = EditMetaDescriptions.Types.Replacement(
					lineFrom = EditDesc.LineFrom().get(),
					lineTo = EditDesc.LineTo().get(),
					oldString = EditDesc.OldString().get(),
					unescapeOld = EditDesc.UnescapeOldString().get(),
					newString = EditDesc.NewString().get(),
					unescapeNew = EditDesc.UnescapeNewString().get()
				)
			)
		)
	)
	
	private val requestSerializer = EditRequest.serializer()
	
	private class ParsedEdit(
		val lineFrom: Int,
		val lineTo: Int,
		val oldString: String,
		val newString: String,
		val range: IntRange,
	)
	
	private class AppliedEdit(
		val start: Int,
		val end: Int,
		val newString: String,
	)
	
	override suspend fun resolve(dependency: DependencyProvider, args: EditArgs): Tool.ResolveResult {
		val request = args as EditArgs.Default
		val fileSystem = dependency.get<FileSystemService>()
		
		val path = trace.catching { fileSystem.normalize(request.filePath) }
			.getOrElse {
				return Rejected(ToolSettings.PathErrorMessage().get()) {
					text(i18n(EditI18n.InvalidPath(), request.filePath))
				}
			}
		
		val displayPath = fileSystem.displayPath(path)
		val shortHash = request.sha256
		if (shortHash.length < 8) return Rejected(ToolSettings.InvalidHash().format(shortHash, shortHash.length)) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		val fileContent = trace.catching { fileSystem.read(path) }
			.getOrElse { e ->
				return Rejected(EditMessage.ReadFailed().format(e.message())) {
					text(i18n(EditI18n.ReadFailed(), displayPath))
				}
			}
		
		if (fileContent.truncated) return Rejected(EditMessage.FileTooLarge().get()) {
			text(i18n(EditI18n.FileTooLarge(), displayPath))
		}
		
		if (!fileContent.sha256.toString().startsWith(shortHash, ignoreCase = true))
			return Rejected(buildString {
				append(EditMessage.HashMismatch().format(shortHash))
				if (shortHash.length > 8) {
					appendLine()
					append(ToolSettings.UseShortHash().format(shortHash.length))
				}
			}) {
				text(i18n(EditI18n.UpdateFailedChanged(), displayPath))
			}
		
		val oldContent = fileContent.content
		
		if (request.edits.isEmpty()) return Rejected(EditMessage.EditsEmpty().get()) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		
		val lastLine = lastLineNumber(oldContent)
		val parsed = mutableListOf<ParsedEdit>()
		val errors = mutableListOf<String>()
		request.edits.forEach { edit ->
			val lineFrom = edit.lineFrom ?: 1
			val lineTo = edit.lineTo ?: lastLine
			fun replacementInvalid(reason: String) {
				errors += EditMessage.ReplacementInvalid().format("$lineFrom-$lineTo") + reason
			}
			if (lineFrom < 1) {
				replacementInvalid(EditMessage.LineFromInvalid().get())
				return@forEach
			}
			if (lineTo < lineFrom) {
				replacementInvalid(EditMessage.LineToInvalid().get())
				return@forEach
			}
			if (lineTo > lastLine) {
				replacementInvalid(EditMessage.LineToOutOfFile().format(lastLine))
				return@forEach
			}
			val oldString = trace.catching { edit.oldString.unescape(edit.unescapeOld) }
				.getOrElse { e ->
					replacementInvalid(EditMessage.InvalidEscape().format("old_string", e.message))
					return@forEach
				}
			if (oldString.isEmpty()) {
				replacementInvalid(EditMessage.OldStringEmpty().get())
				return@forEach
			}
			val newString = trace.catching { edit.newString.unescape(edit.unescapeNew) }
				.getOrElse { e ->
					replacementInvalid(EditMessage.InvalidEscape().format("new_string", e.message))
					return@forEach
				}
			parsed += ParsedEdit(
				lineFrom,
				lineTo,
				oldString,
				newString,
				lineRange(oldContent, lineFrom, lineTo)
			)
		}
		
		if (errors.isNotEmpty()) return Rejected(buildString {
			appendLine(EditMessage.HasInvalidReplacement().format(errors.count()))
			errors.forEach {
				appendLine(it)
			}
		}) {
			text(i18n(EditI18n.InvalidArg(), displayPath))
		}
		
		val ordered = parsed.sortedBy { it.range.first }
		
		for (i in 1 until ordered.size) {
			val previous = ordered[i - 1]
			val current = ordered[i]
			if (previous.range.last + 1 > current.range.first)
				return Rejected(
					EditMessage.ReplacementDuplicate().format(
						"${previous.lineFrom}-${previous.lineTo}",
						"${current.lineFrom}-${current.lineTo}"
					)
				) {
					text(i18n(EditI18n.InvalidArg(), displayPath))
				}
		}
		
		val noMatch = mutableListOf<Pair<Int, Int>>()
		val notUnique = mutableListOf<Pair<Int, Int>>()
		val applied = mutableListOf<AppliedEdit>()
		
		ordered.forEach { edit ->
			val fragment = oldContent.substring(edit.range.first, edit.range.last + 1)
			val matchIndex = fragment.indexOf(edit.oldString)
			when {
				matchIndex == -1 -> noMatch += edit.lineFrom to edit.lineTo
				matchIndex != fragment.lastIndexOf(edit.oldString) -> notUnique += edit.lineFrom to edit.lineTo
				else -> {
					val start = edit.range.first + matchIndex
					applied += AppliedEdit(start, start + edit.oldString.length, edit.newString)
				}
			}
		}
		
		val newContent = spliceContent(oldContent, applied)
		
		if (applied.isEmpty())
			return Rejected(matchMessages(noMatch, notUnique)) {
				text(i18n(EditI18n.MatchFailed(), displayPath))
			}
		
		return Ready(
			requestSerializer,
			EditRequest(
				path,
				displayPath,
				oldContent to fileContent.sha256,
				newContent,
				noMatch,
				notUnique
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
	
	private fun lineRange(content: String, lineFrom: Int, lineTo: Int): IntRange {
		var start = 0
		repeat(lineFrom - 1) {
			val next = content.indexOf('\n', start)
			start = if (next == -1) content.length else next + 1
		}
		var cursor = start
		repeat(lineTo - lineFrom) {
			val next = content.indexOf('\n', cursor)
			cursor = if (next == -1) content.length else next + 1
		}
		val next = content.indexOf('\n', cursor)
		return start until if (next == -1) content.length else next
	}
	
	private fun lastLineNumber(content: String): Int {
		if (content.isEmpty()) return 1
		return content.count { it == '\n' } + if (content.endsWith('\n')) 0 else 1
	}
	
	private fun spliceContent(content: String, applied: List<AppliedEdit>): String {
		if (applied.isEmpty()) return content
		val builder = StringBuilder(content.length)
		var cursor = 0
		for (edit in applied.sortedBy { it.start }) {
			builder.append(content, cursor, edit.start)
			builder.append(edit.newString)
			cursor = edit.end
		}
		builder.append(content, cursor, content.length)
		return builder.toString()
	}
	
	override suspend fun execute(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val request = Json.decodeFromJsonElement(requestSerializer, request)
		val fileSystem = dependency.get<FileSystemService>()
		val oldContent = request.expected.first
		val expected = request.expected.second
		val sha256 = fileSystem.update(request.path, expected, request.newContent)
		val diffText = unifiedDiff(oldContent, request.newContent) ?: EditMessage.Unchanged().get()
		val result = buildString {
			append(EditMessage.Updated().format(request.displayPath, sha256, diffText))
			val skipped = matchMessages(request.skippedNoMatch, request.skippedNotUnique)
			if (skipped.isNotEmpty()) append("\n\n").append(skipped)
		}
		return result.toolSuccess {
			text(i18n(EditI18n.Updated(), request.displayPath))
			diff(request.path, oldContent, request.newContent)
		}
	}
	
	private fun matchMessages(noMatch: List<Pair<Int, Int>>, notUnique: List<Pair<Int, Int>>) = buildString {
		if (noMatch.isNotEmpty())
			appendLine(EditMessage.NoMatch().format(joinedRanges(noMatch)))
		if (noMatch.isNotEmpty() && notUnique.isNotEmpty()) appendLine()
		if (notUnique.isNotEmpty())
			appendLine(EditMessage.NotUnique().format(joinedRanges(notUnique)))
	}
	
	private fun joinedRanges(ranges: List<Pair<Int, Int>>) =
		ranges.joinToString { "${it.first}-${it.second}" }
}
