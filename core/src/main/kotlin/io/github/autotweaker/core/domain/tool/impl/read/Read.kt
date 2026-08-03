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
import io.github.autotweaker.api.base.getOrDefault
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.generated.tool.args.ReadArgs
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolFail
import io.github.autotweaker.api.tool.toolSuccess
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.core.domain.port.FileAccessDeniedException
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.coroutines.channels.Channel
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
	
	override suspend fun coreExec(
		container: DependencyProvider, args: ReadArgs, outputChannel: Channel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val filePath = when (args) {
			is ReadArgs.File -> args.filePath
			is ReadArgs.Summarize -> args.filePath
		}
		val fs = container.get<FileSystemService>()
		val normalizedPath = trace.catching { fs.normalize(filePath) }
			.getOrElse { return ToolSettings.PathErrorMessage().get().toolFail() }
		trace.catching {
			if (!fs.exists(normalizedPath))
				return ReadSettings.MessageFileNotFound().get()
					.format(normalizedPath).toolFail()
			if (!fs.isRegularFile(normalizedPath))
				return ReadSettings.MessageNotRegularFile().get()
					.format(normalizedPath).toolFail()
		}.recoverException { _: PathOutsideWorkspaceException ->
			return ReadSettings.MessagePathOutsideWorkspace().get().toolFail()
		}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return ReadSettings.MessageStartLineError().get().toolFail()
				if (args.endLine < args.startLine) return ReadSettings.MessageStartLineBiggerThanEnd().get()
					.toolFail()
				executeFile(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return ReadSettings.MessageStartLineError().get().toolFail()
				if (args.endLine < args.startLine) return ReadSettings.MessageStartLineBiggerThanEnd().get()
					.toolFail()
				executeSummarize(container, fs, normalizedPath, args)
			}
		}
	}
	
	private suspend fun executeFile(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.File,
	): Tool.ToolOutput {
		val fileMaxLines = ReadSettings.MaxReadLines().get()
		if (args.endLine - args.startLine + 1 > fileMaxLines)
			return ReadSettings.MessageTooManyLines().get().format(fileMaxLines).toolFail()
		
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = ReadSettings.MaxReadChars().get(),
				truncateMessage = ReadSettings.TruncateMessage().get(),
				lineNumber = args.lineNumber ?: true,
				unicodeEscape = args.unicodeEscape ?: false
			)
		}.rethrowCancellation()
			.recoverException { e: StartLineException ->
				return ReadSettings.MessageStartLineBiggerThanFile().get()
					.format(e.lineCount).toolFail()
			}.recoverException { _: FileAccessDeniedException ->
				return ReadSettings.MessageFileAccessDenied().get().toolFail()
			}.getOrElse { e ->
				return ReadSettings.MessageFileCannotRead().get()
					.format(normalizedPath, e.message()).toolFail()
			}
		val sha256 = trace.catching { fs.sha256(normalizedPath) }
			.getOrElse { e ->
				return ReadSettings.MessageFileCannotRead().get()
					.format(normalizedPath, e.message()).toolFail()
			}
		
		val history = container.get<ToolCallHistory>()
		val duplicate = history.getAll(this, ReadArgs.serializer())
			.mapNotNull {
				if (it.args is ReadArgs.File
					&& (it.args.lineNumber ?: true) == (args.lineNumber ?: true)
					&& (it.args.unicodeEscape ?: false) == (args.unicodeEscape ?: false)
				) it.args to it.resultContent
				else null
			}.any { (fileArgs, resultContent) ->
				trace.catching {
					fs.normalize(fileArgs.filePath) == normalizedPath
							&& resultContent.substringBefore('\n') == sha256.toString()
							&& fileArgs.startLine <= args.startLine
							&& fileArgs.endLine >= args.endLine
				}.getOrDefault(false)
			}
		
		if (duplicate) return ReadSettings.DuplicateMessage().get().format(sha256).toolSuccess()
		
		return "$sha256\n$content".toolSuccess()
	}
	
	private suspend fun executeSummarize(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = ReadSettings.SummarizeMaxLines().get()
		if (args.endLine - args.startLine + 1 > summarizeMaxLines)
			return ReadSettings.MessageTooManyLines().get().format(summarizeMaxLines).toolFail()
		
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = ReadSettings.SummarizeMaxInputChars().get(),
				truncateMessage = ReadSettings.SummarizeInputTruncationMessage().get(),
				lineNumber = true,
				unicodeEscape = false
			)
		}.rethrowCancellation()
			.recoverException { e: StartLineException ->
				return ReadSettings.MessageStartLineBiggerThanFile().get()
					.format(e.lineCount).toolFail()
			}.recoverException { _: FileAccessDeniedException ->
				return ReadSettings.MessageFileAccessDenied().get().toolFail()
			}.getOrElse { e ->
				return ReadSettings.MessageFileCannotRead().get()
					.format(normalizedPath, e.message()).toolFail()
			}
		
		val summarizeMinChars = ReadSettings.SummarizeMinChars().get()
		
		if (content.length < summarizeMinChars)
			return ReadSettings.MessageTooFew().get().format(
				content.length, summarizeMinChars
			).toolFail()
		
		val summarizePrompt = ReadSettings.SummarizePrompt().get()
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarize = container.get<SummarizeService>()
		val output = trace.catching { summarize(content, prompt) }.getOrElse { e ->
			return ReadSettings.MessageSummarizeFailed().get().format(e.message()).toolFail()
		}
		val summarizeMaxOutputChars = ReadSettings.SummarizeMaxOutputChars().get()
		return if (output.length > summarizeMaxOutputChars)
			(output.take(summarizeMaxOutputChars)
					+ ReadSettings.SummarizeOutputTruncationMessage().get().format(output.length)
					).toolSuccess()
		else output.toolSuccess()
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean, unicodeEscape: Boolean
	): String {
		val allLines: List<String> = trace.catching { fs.readAllLines(path) }
			.getOrElse { e -> throw IllegalStateException("Failed to read: $e") }
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
	
	private class StartLineException(val lineCount: Int) :
		IllegalStateException("Start line bigger than file size")
}
