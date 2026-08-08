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

package io.github.autotweaker.core.domain.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.CatchingResult
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.generated.tool.args.ReadArgs
import io.github.autotweaker.api.tool.*
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.api.types.tool.read.ReadRequest
import io.github.autotweaker.core.domain.port.FileAccessDeniedException
import io.github.autotweaker.core.domain.port.FileNotFoundException
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

@AutoService(CoreTool::class)
class Read : CoreTool<ReadArgs>, Loggable, Traceable {
	override suspend fun meta() = readMeta(
		ReadMetaDescriptions(
			toolDescription = ReadSettings.ToolDescription().get(),
			functions = ReadMetaDescriptions.Functions(
				file = ReadMetaDescriptions.Functions.File(
					filePath = ToolSettings.FilePathDesc().get(),
					startLine = ReadSettings.StartLineDesc().get(),
					endLine = ReadSettings.EndLineDesc().get(),
					lineNumber = ReadSettings.LineNumberDesc().get(),
					unicodeEscape = ReadSettings.UnicodeEscapeDesc().get()
				) to ReadSettings.ReadFileDescription().get().format(
					ReadSettings.MaxReadChars().get(),
					ReadSettings.MaxReadLines().get()
				
				),
				summarize = ReadMetaDescriptions.Functions.Summarize(
					filePath = ToolSettings.FilePathDesc().get(),
					startLine = ReadSettings.StartLineDesc().get(),
					endLine = ReadSettings.EndLineDesc().get(),
					prompt = ReadSettings.SummarizePromptDesc().get(),
				) to ReadSettings.ReadSummarizeDescription().get().format(
					ReadSettings.SummarizeMaxInputChars().get(),
					ReadSettings.SummarizeMinChars().get(),
					ReadSettings.SummarizeMaxLines().get()
				)
			),
		)
	)
	
	val requestSerializer = ReadRequest.serializer()
	
	override suspend fun coreResolve(
		dependency: DependencyProvider, args: ReadArgs
	): Tool.ResolveResult {
		val fs = dependency.get<FileSystemService>()
		
		val filePath = trace.catching {
			fs.normalize(
				when (args) {
					is ReadArgs.File -> args.filePath
					is ReadArgs.Summarize -> args.filePath
				}
			)
		}.rethrowCancellation().getOrElse {
			return Rejected(ToolSettings.PathErrorMessage())
		}
		
		val request = when (args) {
			is ReadArgs.File -> ReadRequest.File(
				path = filePath,
				startLine = args.startLine,
				endLine = args.endLine,
				lineNumber = args.lineNumber ?: true,
				unicodeEscape = args.unicodeEscape ?: false
			)
			
			is ReadArgs.Summarize -> ReadRequest.Summarize(
				path = filePath,
				startLine = args.startLine,
				endLine = args.endLine,
				prompt = args.prompt
			)
		}
		
		if (request.startLine < 1) return Rejected(ReadSettings.MessageStartLineError())
		if (request.endLine < request.startLine) return Rejected(ReadSettings.MessageStartLineBiggerThanEnd())
		
		val maxLines = when (request) {
			is ReadRequest.File -> ReadSettings.MaxReadLines()
			is ReadRequest.Summarize -> ReadSettings.SummarizeMaxLines()
		}.get()
		if (request.endLine - request.startLine + 1 > maxLines)
			return Rejected(ReadSettings.MessageTooManyLines(), maxLines)
		
		trace.catching {
			if (!fs.exists(filePath))
				return Rejected(ReadSettings.MessageFileNotFound(), filePath)
			if (!fs.isRegularFile(filePath))
				return Rejected(ReadSettings.MessageNotRegularFile(), filePath)
		}.rethrowCancellation().recoverException { _: PathOutsideWorkspaceException ->
			return Rejected(ReadSettings.MessagePathOutsideWorkspace())
		}
		
		return Ready(requestSerializer, request)
	}
	
	override suspend fun coreExec(
		dependency: DependencyProvider, request: JsonElement, outputChannel: Channel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		//准备
		val fs = dependency.get<FileSystemService>()
		val request = Json.decodeFromJsonElement(requestSerializer, request)
		
		var sha256: Sha256? = null
		//read-file判重
		if (request is ReadRequest.File) {
			sha256 = trace.catching {
				fs.sha256(request.path)
			}.onFsException(request.path) { return it }
			
			val history = dependency.get<ToolCallHistory>()
			val duplicate = history.getAll(requestSerializer)
				.any {
					it.request is ReadRequest.File
							&& it.request.lineNumber == request.lineNumber
							&& it.request.unicodeEscape == request.unicodeEscape
							&& it.request.path == request.path
							&& it.resultContent.substringBefore('\n') == sha256.toString()
							&& it.request.startLine <= request.startLine
							&& it.request.endLine >= request.endLine
				}
			
			if (duplicate) return ReadSettings.DuplicateMessage().get().format(sha256).toolSuccess()
		}
		
		//读内容
		val fileContent = trace.catching {
			readFileContent(
				fs,
				request.path,
				request.startLine,
				request.endLine,
				maxChars = when (request) {
					is ReadRequest.File -> ReadSettings.MaxReadChars()
					is ReadRequest.Summarize -> ReadSettings.SummarizeMaxInputChars()
				}.get(),
				truncateMessage = when (request) {
					is ReadRequest.File -> ReadSettings.TruncateMessage()
					is ReadRequest.Summarize -> ReadSettings.SummarizeInputTruncationMessage()
				}.get(),
				lineNumber = request.lineNumber,
				unicodeEscape = request.unicodeEscape
			)
		}.onFsException(request.path) { return it }
		
		when (request) {
			//read-file直接返回
			is ReadRequest.File -> return "$sha256\n$fileContent".toolSuccess()
			is ReadRequest.Summarize -> {
				//最小字符数检查
				val summarizeMinChars = ReadSettings.SummarizeMinChars().get()
				if (fileContent.length < summarizeMinChars)
					return ReadSettings.MessageTooFew().get().format(
						fileContent.length, summarizeMinChars
					).toolFail()
				//提示词构造
				val summarizePrompt = ReadSettings.SummarizePrompt().get()
				val prompt = request.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
				//运行总结
				val summarize = dependency.get<SummarizeService>()
				val output = trace.catching { summarize(fileContent, prompt) }.getOrElse { e ->
					return ReadSettings.MessageSummarizeFailed().get().format(e.message()).toolFail()
				}
				//输出截断
				val summarizeMaxOutputChars = ReadSettings.SummarizeMaxOutputChars().get()
				val result = if (output.length > summarizeMaxOutputChars)
					output.take(summarizeMaxOutputChars) +
							ReadSettings.SummarizeOutputTruncationMessage().get().format(output.length)
				else output
				return result.toolSuccess()
			}
		}
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean, unicodeEscape: Boolean
	): String {
		val allLines: List<String> = trace.catching {
			fs.readAllLines(path)
		}.getOrThrow()
		val lineCount = allLines.size
		if (lineCount < startLine) throw StartLineException(lineCount)
		val actualEndLine = minOf(endLine, lineCount)
		val selectedLines = allLines.subList(startLine - 1, actualEndLine)
		val sb = StringBuilder()
		for (i in selectedLines.indices) {
			val line = if (lineNumber) "${startLine + i}\t${selectedLines[i]}"
			else selectedLines[i]
			sb.appendLine(
				if (unicodeEscape) line.toUnicodeEscape()
				else line
			)
			if (sb.length > maxChars) {
				sb.append(truncateMessage.format(sb.length))
				break
			}
		}
		return sb.toString()
	}
	
	private inline fun <T> CatchingResult<T>.onFsException(
		path: Path, output: (Tool.ToolOutput) -> Unit
	) = rethrowCancellation()
		.onException { _: PathOutsideWorkspaceException ->
			output(ReadSettings.MessagePathOutsideWorkspace().get().toolFail())
		}
		.onException { e: StartLineException ->
			output(
				ReadSettings.MessageStartLineBiggerThanFile().get()
					.format(e.lineCount).toolFail()
			)
		}.onException { _: FileAccessDeniedException ->
			output(ReadSettings.MessageFileAccessDenied().get().toolFail())
		}.onException { _: FileNotFoundException ->
			output(ReadSettings.MessageFileNotFound().get().format(path).toolFail())
		}.getOrElse { e ->
			output(
				ReadSettings.MessageFileCannotRead().get()
					.format(path, e.message()).toolFail()
			)
			unreachable()
		}
	
	private class StartLineException(val lineCount: Int) :
		IllegalStateException("Start line bigger than file size")
}
