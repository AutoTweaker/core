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
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import kotlinx.coroutines.channels.Channel
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@AutoService(CoreTool::class)
class Read : CoreTool<Read.Args> {
	@Serializable
	sealed class Args {
		@Serializable
		data class File(
			val filePath: String,
			val startLine: Int,
			val endLine: Int,
			val lineNumber: Boolean = true,
		) : Args()
		
		@Serializable
		data class Summarize(
			val filePath: String,
			val startLine: Int,
			val endLine: Int,
			val prompt: String? = null,
		) : Args()
		
		@Serializable
		data class Unicode(
			val filePath: String,
			val maxChars: Int,
		) : Args()
	}
	
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var settings: SettingService
	
	override val argsSerializer = Args.serializer()
	override val name = "read"
	override val description get() = settings.get(ReadSettings.DescriptionSetting()).value
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
		Args.File::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		Args.File::startLine to settings.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		Args.File::endLine to settings.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		Args.File::lineNumber to settings.get(ReadSettings.LineNumberPropDescriptionSetting()).value,
		Args.Summarize::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		Args.Summarize::startLine to settings.get(ReadSettings.StartLinePropDescriptionSetting()).value,
		Args.Summarize::endLine to settings.get(ReadSettings.EndLinePropDescriptionSetting()).value,
		Args.Summarize::prompt to settings.get(ReadSettings.SummarizePromptPropDescriptionSetting()).value,
		Args.Unicode::filePath to settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
		Args.Unicode::maxChars to settings.get(ReadSettings.UnicodeMaxCharsPropDescriptionSetting())
			.value.format(settings.get(ReadSettings.UnicodeMaxCharsSetting()).value),
	)
	
	override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
		Args.File::class to settings.get(ReadSettings.FileFuncDescriptionSetting()).value.format(
			settings.get(ReadSettings.FileMaxCharsSetting()).value,
			settings.get(ReadSettings.FileMaxLinesSetting()).value
		),
		Args.Summarize::class to settings.get(ReadSettings.SummarizeFuncDescriptionSetting()).value.format(
			settings.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
			settings.get(ReadSettings.SummarizeMinCharsSetting()).value,
			settings.get(ReadSettings.SummarizeMaxLinesSetting()).value
		),
		Args.Unicode::class to settings.get(ReadSettings.UnicodeFuncDescriptionSetting()).value,
	)
	
	override suspend fun init(service: SettingService, secretStore: SecretStore) {
		settings = service
	}
	
	override suspend fun coreExec(container: SimpleContainer, args: Args, outputChannel: Channel<Tool.RuntimeOutput>?): Tool.ToolOutput {
		val s = settings
		val filePath = when (args) {
			is Args.File -> args.filePath
			is Args.Summarize -> args.filePath
			is Args.Unicode -> args.filePath
		}
		val fs = container.get<FileSystemService>()
		val normalizedPath = try {
			fs.normalize(filePath)
		} catch (_: Exception) {
			return Tool.ToolOutput(s.get(ToolSettings.PathErrorMessage()).value, false)
		}
		try {
			if (!fs.exists(normalizedPath)) {
				return Tool.ToolOutput(s.get(ReadSettings.MessageFileNotFoundSetting()).value, false)
			}
			if (!fs.isRegularFile(normalizedPath)) {
				return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
			}
		} catch (_: FileSystemService.PathOutsideWorkspaceException) {
			return Tool.ToolOutput(s.get(ReadSettings.MessagePathOutsideWorkspaceSetting()).value, false)
		}
		
		logger.debug("Read tool started  tool=read  function={}  filePath={}", args::class.simpleName, filePath)
		
		return when (args) {
			is Args.File -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeFile(s, container, fs, normalizedPath, args)
			}
			
			is Args.Summarize -> {
				if (args.startLine < 1) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (args.endLine < args.startLine) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				executeSummarize(s, container, fs, normalizedPath, args)
			}
			
			is Args.Unicode -> {
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
		args: Args.File,
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
		} catch (_: IllegalStateException) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		val sha256 = try {
			fs.sha256(normalizedPath)
		} catch (_: Exception) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		
		val history = container.get<ToolCallHistory>()
		val previousReads = history.getAll(name, Args.serializer())
			.mapNotNull { entry -> (entry.args as? Args.File)?.let { entry to it } }
			.filter { (_, fileArgs) -> fileArgs.lineNumber == args.lineNumber }
		if (previousReads.any { (entry, fileArgs) ->
				try {
					val prevNormalized = fs.normalize(fileArgs.filePath)
					prevNormalized == normalizedPath && entry.resultContent.substringBefore('\n') == sha256 && fileArgs.startLine <= args.startLine && fileArgs.endLine >= args.endLine
				} catch (_: Exception) {
					false
				}
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
		args: Args.Summarize,
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
		} catch (_: IllegalStateException) {
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
		val output = try {
			summarizeService.summarize(content, prompt)
		} catch (e: Exception) {
			return Tool.ToolOutput(s.get(ReadSettings.SummarizeMessageFailedSetting()).value.format(e.message), false)
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
		val allUnicode: List<Unicode> = try {
			fs.readUnicode(normalizedPath)
		} catch (_: Exception) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		return Tool.ToolOutput(allUnicode.take(maxChars).joinToString("") { it.value }, true)
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService, path: Path, startLine: Int, endLine: Int,
		maxChars: Int, truncateMessage: String, lineNumber: Boolean,
	): String {
		val allLines: List<String> = try {
			fs.readAllLines(path)
		} catch (e: Exception) {
			throw IllegalStateException("Failed to read: $e")
		}
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
