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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.api.types.tool.args.ReadArgs
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@AutoService(CoreTool::class)
class Read : CoreTool<ReadArgs>, Loggable, Traceable, Settable {
	override val argsSerializer = ReadArgs.serializer()
	override val name = "read"
	override val description get() = setting(ReadSettings.DescriptionSetting())
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
		ReadArgs.File::filePath to setting(ReadSettings.FilePathPropDescriptionSetting()),
		ReadArgs.File::startLine to setting(ReadSettings.StartLinePropDescriptionSetting()),
		ReadArgs.File::endLine to setting(ReadSettings.EndLinePropDescriptionSetting()),
		ReadArgs.File::lineNumber to setting(ReadSettings.LineNumberPropDescriptionSetting()),
		ReadArgs.Summarize::filePath to setting(ReadSettings.FilePathPropDescriptionSetting()),
		ReadArgs.Summarize::startLine to setting(ReadSettings.StartLinePropDescriptionSetting()),
		ReadArgs.Summarize::endLine to setting(ReadSettings.EndLinePropDescriptionSetting()),
		ReadArgs.Summarize::prompt to setting(ReadSettings.SummarizePromptPropDescriptionSetting()),
		ReadArgs.Unicode::filePath to setting(ReadSettings.FilePathPropDescriptionSetting()),
		ReadArgs.Unicode::startChar to setting(ReadSettings.UnicodeStartCharPropDescriptionSetting()),
		ReadArgs.Unicode::maxChars to setting(ReadSettings.UnicodeMaxCharsPropDescriptionSetting())
			.format(setting(ReadSettings.UnicodeMaxCharsSetting())),
	)
	
	override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
		ReadArgs.File::class to setting(ReadSettings.FileFuncDescriptionSetting()).format(
			setting(ReadSettings.FileMaxCharsSetting()),
			setting(ReadSettings.FileMaxLinesSetting())
		),
		ReadArgs.Summarize::class to setting(ReadSettings.SummarizeFuncDescriptionSetting()).format(
			setting(ReadSettings.SummarizeMaxInputCharsSetting()),
			setting(ReadSettings.SummarizeMinCharsSetting()),
			setting(ReadSettings.SummarizeMaxLinesSetting())
		),
		ReadArgs.Unicode::class to setting(ReadSettings.UnicodeFuncDescriptionSetting()),
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
			.getOrElse { return Tool.ToolOutput(setting(ToolSettings.PathErrorMessage()), false) }
		trace.catching {
			if (!fs.exists(normalizedPath)) {
				return Tool.ToolOutput(setting(ReadSettings.MessageFileNotFoundSetting()), false)
			}
			if (!fs.isRegularFile(normalizedPath)) {
				return Tool.ToolOutput(setting(ReadSettings.MessageFileCannotReadSetting()), false)
			}
		}.rethrowNot<PathOutsideWorkspaceException>()
			.getOrElse {
				return Tool.ToolOutput(
					setting(ReadSettings.MessagePathOutsideWorkspaceSetting()),
					false
				)
			}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					setting(ReadSettings.MessageStartLineErrorSetting()), false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					setting(ReadSettings.MessageStartLineBiggerThanEndSetting()), false
				)
				executeFile(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					setting(ReadSettings.MessageStartLineErrorSetting()), false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					setting(ReadSettings.MessageStartLineBiggerThanEndSetting()), false
				)
				executeSummarize(container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Unicode -> {
				if (args.startChar < 0) return Tool.ToolOutput(
					setting(ReadSettings.MessageStartCharErrorSetting()), false
				)
				val unicodeMaxChars = setting(ReadSettings.UnicodeMaxCharsSetting())
				if (args.maxChars > unicodeMaxChars) {
					return Tool.ToolOutput(
						setting(ReadSettings.UnicodeMessageTooManyCharsSetting()).format(unicodeMaxChars),
						false
					)
				}
				executeUnicode(fs, normalizedPath, args.startChar, args.maxChars)
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
			return Tool.ToolOutput(
				setting(ReadSettings.MessageTooManyLinesSetting()).format(fileMaxLines),
				false
			)
		}
		val content = trace.catching {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = setting(ReadSettings.FileMaxCharsSetting()),
				truncateMessage = setting(ReadSettings.FileMessageTruncateSetting()),
				lineNumber = args.lineNumber
			)
		}.rethrowCancellation()
			.getOrElse { return Tool.ToolOutput(setting(ReadSettings.MessageFileCannotReadSetting()), false) }
		val sha256 = trace.catching { fs.sha256(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(setting(ReadSettings.MessageFileCannotReadSetting()), false) }
		
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
			return Tool.ToolOutput(setting(ReadSettings.FileMessageDuplicateSetting()).format(sha256), true)
		}
		return Tool.ToolOutput("$sha256\n$content", true)
	}
	
	private suspend fun executeSummarize(
		container: DependencyProvider,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = setting(ReadSettings.SummarizeMaxLinesSetting())
		if (args.endLine - args.startLine + 1 > summarizeMaxLines) {
			return Tool.ToolOutput(
				setting(ReadSettings.MessageTooManyLinesSetting()).format(summarizeMaxLines), false
			)
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
			.getOrElse { return Tool.ToolOutput(setting(ReadSettings.MessageFileCannotReadSetting()), false) }
		val summarizeMinChars = setting(ReadSettings.SummarizeMinCharsSetting())
		if (content.length < summarizeMinChars) {
			return Tool.ToolOutput(
				setting(ReadSettings.SummarizeMessageTooFewSetting()).format(
					content.length, summarizeMinChars
				), false
			)
		}
		val summarizePrompt = setting(ReadSettings.SummarizePromptSetting())
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarize = container.get<SummarizeService>()
		val output = trace.catching { summarize(content, prompt) }
			.getOrElse { e ->
				return Tool.ToolOutput(
					setting(ReadSettings.SummarizeMessageFailedSetting()).format(e.message),
					false
				)
			}
		val summarizeMaxOutputChars = setting(ReadSettings.SummarizeMaxOutputCharsSetting())
		return if (output.length > summarizeMaxOutputChars) {
			Tool.ToolOutput(
				output.take(summarizeMaxOutputChars) + setting(ReadSettings.SummarizeMessageOutputTruncateSetting()).format(
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
		startChar: Int,
		maxChars: Int,
	): Tool.ToolOutput {
		val allUnicode: List<Unicode> = trace.catching { fs.readUnicode(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(setting(ReadSettings.MessageFileCannotReadSetting()), false) }
		return Tool.ToolOutput(allUnicode.drop(startChar).take(maxChars).joinToString("") { it.value }, true)
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
