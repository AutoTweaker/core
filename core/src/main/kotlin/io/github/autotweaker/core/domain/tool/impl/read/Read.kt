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
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.api.types.tool.args.ReadArgs
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log

@AutoService(CoreTool::class)
class Read : CoreTool<ReadArgs>, Loggable {
	private val trace = TraceRecorderImpl.recorder(this::class)
	private lateinit var settings: SettingService
	
	override val argsSerializer = ReadArgs.serializer()
	override val name = "read"
	override val description get() = settings.get(ReadSettings.DescriptionSetting()).value
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
		ReadArgs.File::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.File::startLine to settings.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		ReadArgs.File::endLine to settings.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		ReadArgs.File::lineNumber to settings.get(ReadSettings.LineNumberPropDescriptionSetting()).value,
		ReadArgs.Summarize::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.Summarize::startLine to settings.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		ReadArgs.Summarize::endLine to settings.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		ReadArgs.Summarize::prompt to settings.get(ReadSettings.SummarizePromptPropDescriptionSetting()).value,
		ReadArgs.Unicode::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		ReadArgs.Unicode::maxChars to settings.get(ReadSettings.UnicodeMaxCharsPropDescriptionSetting())
			.value.format(settings.get(ReadSettings.UnicodeMaxCharsSetting()).value),
	)
	
	override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
		ReadArgs.File::class to settings.get(ReadSettings.FileFuncDescriptionSetting()).value.format(
			settings.get(ReadSettings.FileMaxCharsSetting()).value,
			settings.get(ReadSettings.FileMaxLinesSetting()).value
		),
		ReadArgs.Summarize::class to settings.get(ReadSettings.SummarizeFuncDescriptionSetting()).value.format(
			settings.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
			settings.get(ReadSettings.SummarizeMinCharsSetting()).value,
			settings.get(ReadSettings.SummarizeMaxLinesSetting()).value
		),
		ReadArgs.Unicode::class to settings.get(ReadSettings.UnicodeFuncDescriptionSetting()).value,
	)
	
	override suspend fun init(service: SettingService, secretStore: SecretStore) {
		settings = service
	}
	
	override suspend fun coreExec(
		container: SimpleContainer,
		args: ReadArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val s = settings
		val filePath = when (args) {
			is ReadArgs.File -> args.filePath
			is ReadArgs.Summarize -> args.filePath
			is ReadArgs.Unicode -> args.filePath
		}
		val fs = container.get<FileSystemService>()
		val normalizedPath = trace.catching { fs.normalize(filePath) }
			.getOrElse { return Tool.ToolOutput(s.get(ToolSettings.PathErrorMessage()).value, false) }
		try {
			if (!fs.exists(normalizedPath)) {
				return Tool.ToolOutput(s.get(ReadSettings.MessageFileNotFoundSetting()).value, false)
			}
			if (!fs.isRegularFile(normalizedPath)) {
				return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
			}
		} catch (e: FileSystemService.PathOutsideWorkspaceException) {
			trace.exception(e)
			return Tool.ToolOutput(s.get(ReadSettings.MessagePathOutsideWorkspaceSetting()).value, false)
		}
		
		log.debug("Started read tool  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is ReadArgs.File -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeFile(s, container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Summarize -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeSummarize(s, container, fs, normalizedPath, args)
			}
			
			is ReadArgs.Unicode -> {
				val unicodeMaxChars = s.get(ReadSettings.UnicodeMaxCharsSetting()).value
				if (args.maxChars > unicodeMaxChars) {
					return Tool.ToolOutput(
						s.get(ReadSettings.UnicodeMessageTooManyCharsSetting()).value.format(unicodeMaxChars), false
					)
				}
				executeUnicode(s, fs, normalizedPath, args.maxChars)
			}
		}
	}
	
	private suspend fun executeFile(
		s: SettingService,
		container: SimpleContainer,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.File,
	): Tool.ToolOutput {
		val fileMaxLines = s.get(ReadSettings.FileMaxLinesSetting()).value
		if (args.endLine - args.startLine + 1 > fileMaxLines) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageTooManyLinesSetting()).value.format(fileMaxLines), false)
		}
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = s.get(ReadSettings.FileMaxCharsSetting()).value,
				truncateMessage = s.get(ReadSettings.FileMessageTruncateSetting()).value,
				lineNumber = args.lineNumber
			)
		} catch (e: CancellationException) {
			trace.exception(e)
			throw e
		} catch (e: IllegalStateException) {
			trace.exception(e)
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		val sha256 = trace.catching { fs.sha256(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
		
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
			return Tool.ToolOutput(s.get(ReadSettings.FileMessageDuplicateSetting()).value.format(sha256), true)
		}
		return Tool.ToolOutput("$sha256\n$content", true)
	}
	
	private suspend fun executeSummarize(
		s: SettingService,
		container: SimpleContainer,
		fs: FileSystemService,
		normalizedPath: Path,
		args: ReadArgs.Summarize,
	): Tool.ToolOutput {
		val summarizeMaxLines = s.get(ReadSettings.SummarizeMaxLinesSetting()).value
		if (args.endLine - args.startLine + 1 > summarizeMaxLines) {
			return Tool.ToolOutput(
				s.get(ReadSettings.MessageTooManyLinesSetting()).value.format(summarizeMaxLines), false
			)
		}
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				args.startLine,
				args.endLine,
				maxChars = s.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
				truncateMessage = s.get(ReadSettings.SummarizeMessageInputTruncateSetting()).value,
				lineNumber = false
			)
		} catch (e: CancellationException) {
			trace.exception(e)
			throw e
		} catch (e: IllegalStateException) {
			trace.exception(e)
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		val summarizeMinChars = s.get(ReadSettings.SummarizeMinCharsSetting()).value
		if (content.length < summarizeMinChars) {
			return Tool.ToolOutput(
				s.get(ReadSettings.SummarizeMessageTooFewSetting()).value.format(
					content.length, summarizeMinChars
				), false
			)
		}
		val summarizePrompt = s.get(ReadSettings.SummarizePromptSetting()).value
		val prompt = args.prompt?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		val summarizeService = container.get<SummarizeService>()
		val output = trace.catching { summarizeService.summarize(content, prompt) }
			.getOrElse { e ->
				return Tool.ToolOutput(
					s.get(ReadSettings.SummarizeMessageFailedSetting()).value.format(e.message),
					false
				)
			}
		val summarizeMaxOutputChars = s.get(ReadSettings.SummarizeMaxOutputCharsSetting()).value
		return if (output.length > summarizeMaxOutputChars) {
			Tool.ToolOutput(
				output.take(summarizeMaxOutputChars) + s.get(ReadSettings.SummarizeMessageOutputTruncateSetting()).value.format(
					output.length
				), true
			)
		} else {
			Tool.ToolOutput(output, true)
		}
	}
	
	private suspend fun executeUnicode(
		s: SettingService,
		fs: FileSystemService,
		normalizedPath: Path,
		maxChars: Int,
	): Tool.ToolOutput {
		val allUnicode: List<Unicode> = trace.catching { fs.readUnicode(normalizedPath) }
			.getOrElse { return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false) }
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