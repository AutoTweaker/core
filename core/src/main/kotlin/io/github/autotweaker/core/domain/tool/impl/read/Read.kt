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
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.generated.tool.args.ReadArgs
import io.github.autotweaker.api.tool.*
import io.github.autotweaker.api.types.tool.read.ReadRequest
import io.github.autotweaker.api.types.tool.read.ReadResult
import io.github.autotweaker.api.types.tool.text
import io.github.autotweaker.core.domain.port.FileContent
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.coroutines.channels.SendChannel
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
				) to ReadSettings.ReadFileDescription().format(
					ReadSettings.MaxReadChars().get(),
					ReadSettings.MaxReadLines().get()
				
				),
				summarize = ReadMetaDescriptions.Functions.Summarize(
					filePath = ToolSettings.FilePathDesc().get(),
					startLine = ReadSettings.StartLineDesc().get(),
					endLine = ReadSettings.EndLineDesc().get(),
					prompt = ReadSettings.SummarizePromptDesc().get(),
				) to ReadSettings.ReadSummarizeDescription().format(
					ReadSettings.SummarizeMaxInputChars().get(),
					ReadSettings.SummarizeMinChars().get(),
					ReadSettings.SummarizeMaxLines().get()
				)
			),
		)
	)
	
	private val requestSerializer = ReadRequest.serializer()
	private val resultSerializer = ReadResult.serializer()
	
	override suspend fun resolve(
		dependency: DependencyProvider, args: ReadArgs
	): Tool.ResolveResult {
		val fs = dependency.get<FileSystemService>()
		
		val requestPath = when (args) {
			is ReadArgs.File -> args.filePath
			is ReadArgs.Summarize -> args.filePath
		}
		val filePath = trace.catching {
			fs.normalize(requestPath)
		}.getOrElse {
			return Rejected(ToolSettings.PathErrorMessage().get()) {
				text(i18n(ReadI18n.InvalidPath(), requestPath))
			}
		}
		
		val displayPath = fs.displayPath(filePath)
		
		val request = when (args) {
			is ReadArgs.File -> {
				val startLine = args.startLine ?: 1
				val endLine = args.endLine ?: (startLine + ReadSettings.MaxReadLines().get() - 1)
				ReadRequest.File(
					path = filePath,
					displayPath = displayPath,
					startLine = startLine,
					endLine = endLine,
					lineNumber = args.lineNumber ?: true,
					unicodeEscape = args.unicodeEscape ?: false
				)
			}
			
			is ReadArgs.Summarize -> {
				val startLine = args.startLine ?: 1
				val endLine = args.endLine ?: (startLine + ReadSettings.SummarizeMaxLines().get() - 1)
				ReadRequest.Summarize(
					path = filePath,
					displayPath = displayPath,
					startLine = startLine,
					endLine = endLine,
					prompt = args.prompt
				)
			}
		}
		
		if (request.startLine < 1)
			return Rejected(ReadSettings.MessageStartLineError().get()) {
				text(i18n(ReadI18n.InvalidArg()))
			}
		if (request.endLine < request.startLine)
			return Rejected(ReadSettings.MessageStartLineBiggerThanEnd().get()) {
				text(i18n(ReadI18n.InvalidArg()))
			}
		
		val maxLines = when (request) {
			is ReadRequest.File -> ReadSettings.MaxReadLines()
			is ReadRequest.Summarize -> ReadSettings.SummarizeMaxLines()
		}.get()
		val requestLines = request.endLine - request.startLine + 1
		if (requestLines > maxLines)
			return Rejected(ReadSettings.MessageTooManyLines().format(requestLines, maxLines)) {
				text(i18n(ReadI18n.TooManyLines(), displayPath, requestLines))
			}
		
		trace.catching {
			if (!fs.exists(filePath))
				return Rejected(ReadSettings.MessageFileNotFound().format(displayPath)) {
					text(i18n(ReadI18n.FileNotFound(), displayPath))
				}
			if (!fs.isRegularFile(filePath))
				return Rejected(ReadSettings.MessageNotRegularFile().format(displayPath)) {
					text(i18n(ReadI18n.FileNotRegular(), displayPath))
				}
		}.rethrowCancellation().getOrElse { e ->
			return Rejected(
				ReadSettings.MessageReadFailed().format(displayPath, e.message())
			) {
				text(i18n(ReadI18n.Failed(), displayPath, e.message()))
			}
		}
		
		return Ready(
			requestSerializer,
			request,
			request = { reason ->
				when (request) {
					is ReadRequest.File -> text(i18n(ReadI18n.Request(), displayPath, reason))
					is ReadRequest.Summarize -> text(i18n(ReadI18n.RequestSummary(), displayPath, reason))
				}
			},
			executing = {
				when (request) {
					is ReadRequest.File -> text(i18n(ReadI18n.Executing(), displayPath))
					is ReadRequest.Summarize -> text(i18n(ReadI18n.ExecutingSummary(), displayPath))
				}
			},
			cancelled = {
				text(i18n(ReadI18n.Cancelled(), displayPath))
			},
			rejected = { reason ->
				if (reason == null) text(i18n(ReadI18n.Rejected(), displayPath))
				else text(i18n(ReadI18n.RejectedWithReason(), displayPath, reason))
			},
			failed = { e ->
				text(i18n(ReadI18n.Failed(), displayPath, e.message()))
			},
			timeout = { elapsed ->
				text(i18n(ReadI18n.Timeout(), displayPath, elapsed))
			},
		)
	}
	
	override suspend fun execute(
		dependency: DependencyProvider, request: JsonElement, outputChannel: SendChannel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		//准备
		val fs = dependency.get<FileSystemService>()
		val request = Json.decodeFromJsonElement(requestSerializer, request)
		
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
		}.rethrowCancellation().onException { e: StartLineException ->
			return ReadSettings.MessageStartLineBiggerThanFile().format(e.lineCount).toolFail {
				text(i18n(ReadI18n.StartLineError(), request.displayPath, e.request, e.lineCount))
			}
		}.getOrElse { e ->
			return ReadSettings.MessageReadFailed().format(request.displayPath, e.message()).toolFail {
				text(i18n(ReadI18n.Failed(), request.displayPath, e.message()))
			}
		}
		
		//read-file判重
		if (request is ReadRequest.File) {
			val history = dependency.get<ToolCallHistory>()
			val duplicate = history.getAll(requestSerializer, resultSerializer)
				.any { (req, result) ->
					req is ReadRequest.File
							&& !result.truncated
							&& req.lineNumber == request.lineNumber
							&& req.unicodeEscape == request.unicodeEscape
							&& req.path == request.path
							&& result.sha256 == fileContent.sha256
							&& req.startLine <= request.startLine
							&& req.endLine >= request.endLine
				}
			
			if (duplicate) return ReadSettings.DuplicateMessage().format(fileContent.sha256).toolSuccess {
				text(i18n(ReadI18n.Executed(), request.displayPath))
			}
		}
		
		when (request) {
			//read-file直接返回
			is ReadRequest.File -> return "${fileContent.sha256}\n${fileContent.content}".toolSuccess(
				resultSerializer, ReadResult(
					fileContent.sha256, fileContent.content, fileContent.truncated
				)
			) {
				text(i18n(ReadI18n.Executed(), request.displayPath))
			}
			
			is ReadRequest.Summarize -> {
				//最小字符数检查
				val summarizeMinChars = ReadSettings.SummarizeMinChars().get()
				if (fileContent.content.length < summarizeMinChars)
					return ReadSettings.MessageTooFew().format(
						fileContent.content.length, summarizeMinChars
					).toolFail {
						text(i18n(ReadI18n.TooFewChars(), request.displayPath, fileContent.content.length))
					}
				//提示词构造
				val summarizePrompt = ReadSettings.SummarizePrompt().get()
				val prompt = request.prompt?.let { "$summarizePrompt\n$it" } ?: summarizePrompt
				//运行总结
				val summarize = dependency.get<SummarizeService>()
				val output = trace.catching { summarize(prompt + '\n' + fileContent.content) }.getOrElse { e ->
					return ReadSettings.MessageSummarizeFailed().format(e.message()).toolFail {
						text(i18n(ReadI18n.SummaryFailed(), request.displayPath, e.message()))
					}
				}
				if (output == null) return ReadSettings.SummarizeOutputEmptyMessage().get()
					.toolFail { text(i18n(ReadI18n.SummaryEmpty(), request.displayPath)) }
				//输出截断
				val summarizeMaxOutputChars = ReadSettings.SummarizeMaxOutputChars().get()
				val result = if (output.length > summarizeMaxOutputChars)
					output.take(summarizeMaxOutputChars) +
							ReadSettings.SummarizeOutputTruncationMessage().format(output.length)
				else output
				return result.toolSuccess {
					text(i18n(ReadI18n.ExecutedSummary(), request.displayPath))
				}
			}
		}
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean, unicodeEscape: Boolean
	): FileContent {
		val result = fs.read(path)
		val allLines = result.content.lines()
		val lineCount = allLines.size
		if (lineCount < startLine) throw StartLineException(startLine, lineCount)
		val actualEndLine = minOf(endLine, lineCount)
		val selectedLines = allLines.subList(startLine - 1, actualEndLine)
		
		val sb = StringBuilder()
		var truncated = false
		for (i in selectedLines.indices) {
			val prefix = if (lineNumber) "${startLine + i}\t" else ""
			val line = if (unicodeEscape) selectedLines[i].toUnicodeEscape()
			else selectedLines[i]
			val remain = maxChars - sb.length - prefix.length - 1
			if (line.length > remain) {
				truncated = true
				if (remain > 0) sb.append(prefix).append(line, 0, remain).append('\n')
				break
			}
			sb.append(prefix).append(line).append('\n')
		}
		if (truncated || result.truncated) sb.append(truncateMessage)
		return FileContent(sb.toString(), truncated || result.truncated, result.sha256)
	}
	
	private class StartLineException(val request: Int, val lineCount: Int) :
		IllegalStateException("Start line bigger than file size")
}
