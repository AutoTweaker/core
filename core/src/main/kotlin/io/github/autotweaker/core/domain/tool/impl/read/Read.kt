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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.getOrDefault
import io.github.autotweaker.api.trace.getOrElse
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.api.types.tool.args.ReadArgs
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@AutoService(CoreTool::class)
class Read : CoreTool<ReadArgs>, Loggable, Traceable, Settable {
	override val argsSerializer = ReadArgs.serializer()
	override val name = "read"
	override val description get() = setting.get(ReadSettings.DescriptionSetting()).value
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
		ReadArgs.File::filePath to setting.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.File::startLine to setting.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		ReadArgs.File::endLine to setting.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		ReadArgs.File::lineNumber to setting.get(ReadSettings.LineNumberPropDescriptionSetting()).value,
		ReadArgs.Summarize::filePath to setting.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.Summarize::startLine to setting.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		ReadArgs.Summarize::endLine to setting.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		ReadArgs.Summarize::prompt to setting.get(ReadSettings.SummarizePromptPropDescriptionSetting()).value,
		ReadArgs.Unicode::filePath to setting.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.Unicode::maxChars to setting.get(ReadSettings.UnicodeMaxCharsPropDescriptionSetting())
			.value.format(setting.get(ReadSettings.UnicodeMaxCharsSetting()).value),
	)
	
	override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
		ReadArgs.File::class to setting.get(ReadSettings.FileFuncDescriptionSetting()).value.format(
			setting.get(ReadSettings.FileMaxCharsSetting()).value,
			setting.get(ReadSettings.FileMaxLinesSetting()).value
		),
		ReadArgs.Summarize::class to setting.get(ReadSettings.SummarizeFuncDescriptionSetting()).value.format(
			setting.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
			setting.get(ReadSettings.SummarizeMinCharsSetting()).value,
			setting.get(ReadSettings.SummarizeMaxLinesSetting()).value
		),
		ReadArgs.Unicode::class to setting.get(ReadSettings.UnicodeFuncDescriptionSetting()).value,
	)
	
	
	override suspend fun coreExec(
		container: DependencyProvider,
		args: ReadArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val filePath = when (args) {
			is ReadArgs.File -> args.filePath
			is ReadArgs.Summarize -> args.filePath
			is ReadArgs.Unicode -> args.filePath
		}
		val fs = container.get<FileSystemService>()
		val normalizedPath = trace.catching { fs.normalize(filePath) }
			.getOrElse { return Tool.ToolOutput(setting.get(ToolSettings.PathErrorMessage()).value, false) }
		trace.catching {
			if (!fs.exists(normalizedPath)) {
				return Tool.ToolOutput(setting.get(ReadSettings.MessageFileNotFoundSetting()).value, false)
			}
			if (!fs.isRegularFile(normalizedPath)) {
				return Tool.ToolOutput(setting.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
			}
		}.rethrowNot<FileSystemService.PathOutsideWorkspaceException>()
			.getOrElse {
				return Tool.ToolOutput(
					setting.get(ReadSettings.MessagePathOutsideWorkspaceSetting()).value,
					false
				)
			}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					setting.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					setting.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeFile(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					setting.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					setting.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeSummarize(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Unicode -> {
				val unicodeMaxChars = setting.get(ReadSettings.UnicodeMaxCharsSetting()).value
				if (args.maxChars > unicodeMaxChars) {
					return Tool.ToolOutput(
						setting.get(ReadSettings.UnicodeMessageTooManyCharsSetting()).value.format(unicodeMaxChars),
						false
					)
				}
				executeUnicode(fs, normalizedPath, args.maxChars)
			}
		}
	}
	
	private suspend fun executeFile(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.File,
	): Tool.ToolOutput {
		val fileMaxLines = setting.get(ReadSettings.FileMaxLinesSetting()).value
		if (args.endLine - args.startLine + 1 > fileMaxLines) {
			return Tool.ToolOutput(
				setting.get(ReadSettings.MessageTooManyLinesSetting()).value.format(fileMaxLines),
				false
			)
		}
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = setting.get(ReadSettings.FileMaxCharsSetting()).value,
				truncateMessage = setting.get(ReadSettings.FileMessageTruncateSetting()).value,
				lineNumber = args.lineNumber
			)
		}.rethrow<CancellationException>()
			.getOrElse { return Tool.ToolOutput(setting.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
		val sha256 = trace.catching { fs.sha256(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(setting.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
		
		val history = container.get<ToolCallHistory>()
		val previousReads = history.getAll(name, ReadArgs.serializer())
			.mapNotNull { entry -> (entry.args as? ReadArgs.File)?.let { entry to it } }
			.filter { (_, fileArgs) -> fileArgs.lineNumber == args.lineNumber }
		if (previousReads.any { (entry, fileArgs) ->
				trace.catching {
					val prevNormalized = fs.normalize(fileArgs.filePath)
					prevNormalized == normalizedPath && entry.resultContent.substringBefore('\n') == sha256 && fileArgs.startLine <= args.startLine && fileArgs.endLine >= args.endLine
				}.getOrDefault(false)
			}) {
			return Tool.ToolOutput(setting.get(ReadSettings.FileMessageDuplicateSetting()).value.format(sha256), true)
		}
		return Tool.ToolOutput("$sha256\n$content", true)
	}
	
	private suspend fun executeSummarize(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = setting.get(ReadSettings.SummarizeMaxLinesSetting()).value
		if (args.endLine - args.startLine + 1 > summarizeMaxLines) {
			return Tool.ToolOutput(
				setting.get(ReadSettings.MessageTooManyLinesSetting()).value.format(summarizeMaxLines), false
			)
		}
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = setting.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
				truncateMessage = setting.get(ReadSettings.SummarizeMessageInputTruncateSetting()).value,
				lineNumber = false
			)
		}.rethrow<CancellationException>()
			.getOrElse { return Tool.ToolOutput(setting.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
		val summarizeMinChars = setting.get(ReadSettings.SummarizeMinCharsSetting()).value
		if (content.length < summarizeMinChars) {
			return Tool.ToolOutput(
				setting.get(ReadSettings.SummarizeMessageTooFewSetting()).value.format(
					content.length, summarizeMinChars
				), false
			)
		}
		val summarizePrompt = setting.get(ReadSettings.SummarizePromptSetting()).value
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarizeService = container.get<SummarizeService>()
		val output = trace.catching { summarizeService.summarize(content, prompt) }
			.getOrElse { e ->
				return Tool.ToolOutput(
					setting.get(ReadSettings.SummarizeMessageFailedSetting()).value.format(e.message),
					false
				)
			}
		val summarizeMaxOutputChars = setting.get(ReadSettings.SummarizeMaxOutputCharsSetting()).value
		return if (output.length > summarizeMaxOutputChars) {
			Tool.ToolOutput(
				output.take(summarizeMaxOutputChars) + setting.get(ReadSettings.SummarizeMessageOutputTruncateSetting()).value.format(
					output.length
				), true
			)
		} else {
			Tool.ToolOutput(output, true)
		}
	}
	
	private suspend fun executeUnicode(
		fs: FileSystemService,
		normalizedPath: Path,
		maxChars: Int,
	): Tool.ToolOutput {
		val allUnicode: List<Unicode> = trace.catching { fs.readUnicode(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(setting.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
		return Tool.ToolOutput(allUnicode.take(maxChars).joinToString("") { it.value }, true)
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
