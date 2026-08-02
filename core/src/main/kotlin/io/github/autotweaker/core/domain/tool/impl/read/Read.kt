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
import io.github.autotweaker.api.generated.tool.args.ReadArgs
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolFail
import io.github.autotweaker.api.tool.toolSuccess
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
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
			toolDescription = ReadSettings.DescriptionSetting().get(),
			functions = ReadMetaDescriptions.Functions(
				file = ReadMetaDescriptions.Functions.File(
					filePath = ToolSettings.FilePathDesc().get(),
					startLine = ReadSettings.StartLinePropDescriptionSetting().get(),
					endLine = ReadSettings.EndLinePropDescriptionSetting().get(),
					lineNumber = ReadSettings.LineNumberPropDescriptionSetting().get(),
					unicodeEscape = ReadSettings.UnicodeEscapePropDescriptionSetting().get()
				) to ReadSettings.FileFuncDescriptionSetting().get().format(
					ReadSettings.FileMaxCharsSetting().get(),
					ReadSettings.FileMaxLinesSetting().get()
				
				),
				summarize = ReadMetaDescriptions.Functions.Summarize(
					filePath = ToolSettings.FilePathDesc().get(),
					startLine = ReadSettings.StartLinePropDescriptionSetting().get(),
					endLine = ReadSettings.EndLinePropDescriptionSetting().get(),
					prompt = ReadSettings.SummarizePromptPropDescriptionSetting().get(),
				) to ReadSettings.SummarizeFuncDescriptionSetting().get().format(
					ReadSettings.SummarizeMaxInputCharsSetting().get(),
					ReadSettings.SummarizeMinCharsSetting().get(),
					ReadSettings.SummarizeMaxLinesSetting().get()
				)
			),
		)
	)
	
	private val fileCannotRead = ReadSettings.MessageFileCannotReadSetting().get().toolFail()
	
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
				return ReadSettings.MessageFileNotFoundSetting().get().toolFail()
			if (!fs.isRegularFile(normalizedPath))
				return fileCannotRead
		}.rethrowNot<PathOutsideWorkspaceException>().getOrElse {
			return ReadSettings.MessagePathOutsideWorkspaceSetting().get().toolFail()
		}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return ReadSettings.MessageStartLineErrorSetting().get().toolFail()
				if (args.endLine < args.startLine) return ReadSettings.MessageStartLineBiggerThanEndSetting().get()
					.toolFail()
				executeFile(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return ReadSettings.MessageStartLineErrorSetting().get().toolFail()
				if (args.endLine < args.startLine) return ReadSettings.MessageStartLineBiggerThanEndSetting().get()
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
		val fileMaxLines = ReadSettings.FileMaxLinesSetting().get()
		if (args.endLine - args.startLine + 1 > fileMaxLines)
			return ReadSettings.MessageTooManyLinesSetting().get().format(fileMaxLines).toolFail()
		
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = ReadSettings.FileMaxCharsSetting().get(),
				truncateMessage = ReadSettings.FileMessageTruncateSetting().get(),
				lineNumber = args.lineNumber ?: true,
				unicodeEscape = args.unicodeEscape ?: false
			)
		}.rethrowCancellation()
			.getOrElse { return fileCannotRead }
		val sha256 = trace.catching { fs.sha256(normalizedPath) }
			.getOrElse { return fileCannotRead }
		
		val history = container.get<ToolCallHistory>()
		val duplicate = history.getAll(this, ReadArgs.serializer())
			.mapNotNull {
				if (it.args is ReadArgs.File && it.args.lineNumber == args.lineNumber)
					it.args to it.resultContent
				else null
			}.any { (fileArgs, resultContent) ->
				trace.catching {
					fs.normalize(fileArgs.filePath) == normalizedPath
							&& resultContent.substringBefore('\n') == sha256.toString()
							&& fileArgs.startLine <= args.startLine
							&& fileArgs.endLine >= args.endLine
				}.getOrDefault(false)
			}
		
		if (duplicate)
			return ReadSettings.FileMessageDuplicateSetting().get().format(sha256).toolSuccess()
		
		return "$sha256\n$content".toolSuccess()
	}
	
	private suspend fun executeSummarize(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = ReadSettings.SummarizeMaxLinesSetting().get()
		if (args.endLine - args.startLine + 1 > summarizeMaxLines)
			return ReadSettings.MessageTooManyLinesSetting().get().format(summarizeMaxLines).toolFail()
		
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = ReadSettings.SummarizeMaxInputCharsSetting().get(),
				truncateMessage = ReadSettings.SummarizeMessageInputTruncateSetting().get(),
				lineNumber = true,
				unicodeEscape = false
			)
		}.rethrowCancellation()
			.getOrElse { return fileCannotRead }
		val summarizeMinChars = ReadSettings.SummarizeMinCharsSetting().get()
		if (content.length < summarizeMinChars)
			return ReadSettings.SummarizeMessageTooFewSetting().get().format(
				content.length, summarizeMinChars
			).toolFail()
		
		val summarizePrompt = ReadSettings.SummarizePromptSetting().get()
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarize = container.get<SummarizeService>()
		val output = trace.catching { summarize(content, prompt) }.getOrElse { e ->
			return ReadSettings.SummarizeMessageFailedSetting().get().format(e.message()).toolFail()
		}
		val summarizeMaxOutputChars = ReadSettings.SummarizeMaxOutputCharsSetting().get()
		return if (output.length > summarizeMaxOutputChars)
			(output.take(summarizeMaxOutputChars) +
					ReadSettings.SummarizeMessageOutputTruncateSetting().get()
						.format(output.length)
					).toolSuccess()
		else output.toolSuccess()
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean, unicodeEscape: Boolean
	): String {
		val allLines: List<String> = trace.catching { fs.readAllLines(path) }
			.getOrElse { e -> throw IllegalStateException("Failed to read: $e") }
		val actualEndLine = minOf(endLine, allLines.size)
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
}
