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
import io.github.autotweaker.api.types.Unicode
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
class Read : CoreTool<ReadArgs>, Loggable, Traceable, Settable {
	override suspend fun meta() = readMeta(
		ReadMetaDescriptions(
			toolDescription = setting(ReadSettings.DescriptionSetting()),
			functions = ReadMetaDescriptions.Functions(
				file = ReadMetaDescriptions.Functions.File(
					filePath = setting(ReadSettings.FilePathPropDescriptionSetting()),
					startLine = setting(ReadSettings.StartLinePropDescriptionSetting()),
					endLine = setting(ReadSettings.EndLinePropDescriptionSetting()),
					lineNumber = setting(ReadSettings.LineNumberPropDescriptionSetting()),
				) to setting(ReadSettings.FileFuncDescriptionSetting()).format(
					setting(ReadSettings.FileMaxCharsSetting()),
					setting(ReadSettings.FileMaxLinesSetting())
				
				),
				summarize = ReadMetaDescriptions.Functions.Summarize(
					filePath = setting(ReadSettings.FilePathPropDescriptionSetting()),
					startLine = setting(ReadSettings.StartLinePropDescriptionSetting()),
					endLine = setting(ReadSettings.EndLinePropDescriptionSetting()),
					prompt = setting(ReadSettings.SummarizePromptPropDescriptionSetting()),
				) to setting(ReadSettings.SummarizeFuncDescriptionSetting()).format(
					setting(ReadSettings.SummarizeMaxInputCharsSetting()),
					setting(ReadSettings.SummarizeMinCharsSetting()),
					setting(ReadSettings.SummarizeMaxLinesSetting())
				),
				unicode = ReadMetaDescriptions.Functions.Unicode(
					filePath = setting(ReadSettings.FilePathPropDescriptionSetting()),
					startChar = setting(ReadSettings.UnicodeStartCharPropDescriptionSetting()),
					maxChars = setting(ReadSettings.UnicodeMaxCharsPropDescriptionSetting()).format(
						setting(ReadSettings.UnicodeMaxCharsSetting())
					),
				) to setting(ReadSettings.UnicodeFuncDescriptionSetting()),
			),
		)
	)
	
	private val fileCannotRead = setting(ReadSettings.MessageFileCannotReadSetting()).toolFail()
	
	override suspend fun coreExec(
		container: DependencyProvider, args: ReadArgs, outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val filePath = when (args) {
			is ReadArgs.File -> args.filePath
			is ReadArgs.Summarize -> args.filePath
			is ReadArgs.Unicode -> args.filePath
		}
		val fs = container.get<FileSystemService>()
		val normalizedPath = trace.catching { fs.normalize(filePath) }
			.getOrElse { return setting(ToolSettings.PathErrorMessage()).toolFail() }
		trace.catching {
			if (!fs.exists(normalizedPath)) {
				return setting(ReadSettings.MessageFileNotFoundSetting()).toolFail()
			}
			if (!fs.isRegularFile(normalizedPath)) {
				return fileCannotRead
			}
		}.rethrowNot<PathOutsideWorkspaceException>().getOrElse {
			return setting(ReadSettings.MessagePathOutsideWorkspaceSetting()).toolFail()
		}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return setting(ReadSettings.MessageStartLineErrorSetting()).toolFail()
				if (args.endLine < args.startLine) return setting(ReadSettings.MessageStartLineBiggerThanEndSetting()).toolFail()
				executeFile(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return setting(ReadSettings.MessageStartLineErrorSetting()).toolFail()
				if (args.endLine < args.startLine) return setting(ReadSettings.MessageStartLineBiggerThanEndSetting()).toolFail()
				executeSummarize(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Unicode -> {
				val startChar = args.startChar ?: 0
				if (startChar < 0) return setting(ReadSettings.MessageStartCharErrorSetting()).toolFail()
				val unicodeMaxChars = setting(ReadSettings.UnicodeMaxCharsSetting())
				if (args.maxChars > unicodeMaxChars) {
					return setting(ReadSettings.UnicodeMessageTooManyCharsSetting()).format(unicodeMaxChars).toolFail()
				}
				executeUnicode(fs, normalizedPath, startChar, args.maxChars)
			}
		}
	}
	
	private suspend fun executeFile(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.File,
	): Tool.ToolOutput {
		val fileMaxLines = setting(ReadSettings.FileMaxLinesSetting())
		if (args.endLine - args.startLine + 1 > fileMaxLines) {
			return setting(ReadSettings.MessageTooManyLinesSetting()).format(fileMaxLines).toolFail()
		}
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = setting(ReadSettings.FileMaxCharsSetting()),
				truncateMessage = setting(ReadSettings.FileMessageTruncateSetting()),
				lineNumber = args.lineNumber ?: true
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
			return setting(ReadSettings.FileMessageDuplicateSetting()).format(sha256).toolSuccess()
		
		return "$sha256\n$content".toolSuccess()
	}
	
	private suspend fun executeSummarize(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = setting(ReadSettings.SummarizeMaxLinesSetting())
		if (args.endLine - args.startLine + 1 > summarizeMaxLines) {
			return setting(ReadSettings.MessageTooManyLinesSetting()).format(summarizeMaxLines).toolFail()
		}
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = setting(ReadSettings.SummarizeMaxInputCharsSetting()),
				truncateMessage = setting(ReadSettings.SummarizeMessageInputTruncateSetting()),
				lineNumber = false
			)
		}.rethrowCancellation()
			.getOrElse { return fileCannotRead }
		val summarizeMinChars = setting(ReadSettings.SummarizeMinCharsSetting())
		if (content.length < summarizeMinChars)
			return setting(ReadSettings.SummarizeMessageTooFewSetting()).format(
				content.length, summarizeMinChars
			).toolFail()
		
		val summarizePrompt = setting(ReadSettings.SummarizePromptSetting())
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarize = container.get<SummarizeService>()
		val output = trace.catching { summarize(content, prompt) }.getOrElse { e ->
			return setting(ReadSettings.SummarizeMessageFailedSetting()).format(e.message).toolFail()
		}
		val summarizeMaxOutputChars = setting(ReadSettings.SummarizeMaxOutputCharsSetting())
		return if (output.length > summarizeMaxOutputChars)
			(output.take(summarizeMaxOutputChars) +
					setting(ReadSettings.SummarizeMessageOutputTruncateSetting())
						.format(output.length)
					).toolSuccess()
		else output.toolSuccess()
	}
	
	private suspend fun executeUnicode(
		fs: FileSystemService,
		normalizedPath: Path,
		startChar: Int,
		maxChars: Int,
	): Tool.ToolOutput {
		val allUnicode: List<Unicode> = trace.catching { fs.readUnicode(normalizedPath) }
			.getOrElse { return fileCannotRead }
		return allUnicode.drop(startChar).take(maxChars)
			.joinToString("") { it.value }.toolSuccess()
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean,
	): String {
		val allLines: List<String> = trace.catching { fs.readAllLines(path) }
			.getOrElse { e -> throw IllegalStateException("Failed to read: $e") }
		val actualEndLine = minOf(endLine, allLines.size)
		val selectedLines = allLines.subList(startLine - 1, actualEndLine)
		val sb = StringBuilder()
		for (i in selectedLines.indices) {
			val line = if (lineNumber) "${startLine + i}\t${selectedLines[i]}" else selectedLines[i]
			sb.appendLine(line)
			if (sb.length > maxChars) {
				sb.append(truncateMessage.format(sb.length))
				break
			}
		}
		return sb.toString().trimEnd()
	}
}
