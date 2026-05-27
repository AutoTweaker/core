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
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.impl.ToolSettings
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Path

@AutoService(CoreTool::class)
class Read : CoreTool {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var _meta: Tool.Meta
	private lateinit var settings: SettingService
	override val meta: Tool.Meta get() = _meta
	
	override fun init(service: SettingService) {
		settings = service
		
		val commonProperties: Map<String, Tool.Function.Property> = mapOf(
			"file_path" to Tool.Function.Property(
				description = settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.StringValue(),
			),
			"start_line" to Tool.Function.Property(
				description = settings.get(ReadSettings.StartLinePropDescriptionSetting()).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
			"end_line" to Tool.Function.Property(
				description = settings.get(ReadSettings.EndLinePropDescriptionSetting()).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
		)
		
		_meta = Tool.Meta(
			name = "read", description = settings.get(ReadSettings.DescriptionSetting()).value, functions = listOf(
				Tool.Function(
					name = "file",
					description = settings.get(ReadSettings.FileFuncDescriptionSetting()).value.format(
						settings.get(ReadSettings.FileMaxCharsSetting()).value,
						settings.get(ReadSettings.FileMaxLinesSetting()).value
					),
					parameters = commonProperties + mapOf(
						"line_number" to Tool.Function.Property(
							description = settings.get(ReadSettings.LineNumberPropDescriptionSetting()).value,
							required = false,
							valueType = Tool.Function.Property.ValueType.BooleanValue,
						),
					),
				),
				Tool.Function(
					name = "summarize",
					description = settings.get(ReadSettings.SummarizeFuncDescriptionSetting()).value.format(
						settings.get(ReadSettings.SummarizeMaxInputCharsSetting()).value,
						settings.get(ReadSettings.SummarizeMinCharsSetting()).value,
						settings.get(ReadSettings.SummarizeMaxLinesSetting()).value
					),
					parameters = commonProperties + mapOf(
						"prompt" to Tool.Function.Property(
							description = settings.get(ReadSettings.SummarizePromptPropDescriptionSetting()).value,
							required = false,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
					),
				),
				Tool.Function(
					name = "unicode",
					description = settings.get(ReadSettings.UnicodeFuncDescriptionSetting()).value,
					parameters = mapOf(
						"file_path" to Tool.Function.Property(
							description = settings.get(ReadSettings.FilePathPropDescriptionSetting()).value,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"max_chars" to Tool.Function.Property(
							description = settings.get(ReadSettings.UnicodeMaxCharsPropDescriptionSetting()).value.format(
								settings.get(ReadSettings.UnicodeMaxCharsSetting()).value
							),
							required = true,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
					),
				),
			)
		)
	}
	
	override suspend fun coreExec(container: SimpleContainer, input: Tool.ToolInput): Tool.ToolOutput {
		val s = settings
		val args = input.arguments
		val functionName = input.functionName
		val filePath = args["file_path"]!!.jsonPrimitive.content
		val fs = container.get<FileSystemService>()
		val normalizedPath = try {
			fs.normalize(filePath)
		} catch (_: Exception) {
			return Tool.ToolOutput(s.get(ToolSettings.PathErrorMessage()).value, false)
		}
		if (!fs.exists(normalizedPath)) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileNotFoundSetting()).value, false)
		}
		if (!fs.isRegularFile(normalizedPath)) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting()).value, false)
		}
		
		logger.debug(
			"Read tool started  tool=read  function={}  filePath={}", functionName, filePath
		)
		
		return when (functionName) {
			"file", "summarize" -> {
				val startLine = args["start_line"]!!.jsonPrimitive.int
				val endLine = args["end_line"]!!.jsonPrimitive.int
				if (startLine < 1) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineErrorSetting()).value, false
				)
				if (endLine < startLine) return Tool.ToolOutput(
					s.get(ReadSettings.MessageStartLineBiggerThanEndSetting()).value, false
				)
				if (functionName == "file") executeFile(s, container, fs, input, normalizedPath, startLine, endLine)
				else executeSummarize(s, container, fs, input, normalizedPath, startLine, endLine)
			}
			
			"unicode" -> {
				val maxChars = args["max_chars"]!!.jsonPrimitive.int
				val unicodeMaxChars = s.get(ReadSettings.UnicodeMaxCharsSetting()).value
				if (maxChars > unicodeMaxChars) {
					return Tool.ToolOutput(
						s.get(ReadSettings.UnicodeMessageTooManyCharsSetting()).value.format(unicodeMaxChars), false
					)
				}
				executeUnicode(s, fs, normalizedPath, maxChars)
			}
			
			else -> throw IllegalArgumentException("Unknown function: $functionName")
		}
	}
	
	private suspend fun executeFile(
		s: SettingService,
		container: SimpleContainer,
		fs: FileSystemService,
		input: Tool.ToolInput,
		normalizedPath: Path,
		startLine: Int,
		endLine: Int,
	): Tool.ToolOutput {
		val fileMaxLines = s.get(ReadSettings.FileMaxLinesSetting()).value
		if (endLine - startLine + 1 > fileMaxLines) {
			return Tool.ToolOutput(s.get(ReadSettings.MessageTooManyLinesSetting()).value.format(fileMaxLines), false)
		}
		val args = input.arguments
		val lineNumber = args["line_number"]?.jsonPrimitive?.booleanOrNull ?: true
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				startLine,
				endLine,
				maxChars = s.get(ReadSettings.FileMaxCharsSetting()).value,
				truncateMessage = s.get(ReadSettings.FileMessageTruncateSetting()).value,
				lineNumber = lineNumber
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
		val previousReads = buildList {
			data class PrevRead(val path: String, val sha256: String, val startLine: Int, val endLine: Int)
			for (entry in history.getAll()) {
				if (entry.name != "read_file") continue
				val toolArgs = try {
					Json.parseToJsonElement(entry.arguments).jsonObject
				} catch (_: Exception) {
					continue
				}
				val toolStartLine = toolArgs["start_line"]?.jsonPrimitive?.int ?: continue
				val toolEndLine = toolArgs["end_line"]?.jsonPrimitive?.int ?: continue
				val toolLineNumber = toolArgs["line_number"]?.jsonPrimitive?.booleanOrNull ?: true
				if (toolLineNumber != lineNumber) continue
				val toolPath = toolArgs["file_path"]?.jsonPrimitive?.content ?: continue
				val toolSha256 = entry.resultContent.substringBefore('\n')
				if (toolSha256.length != 64) continue
				add(PrevRead(toolPath, toolSha256, toolStartLine, toolEndLine))
			}
		}
		if (previousReads.any { prev ->
				try {
					val prevNormalized = fs.normalize(prev.path)
					prevNormalized == normalizedPath && prev.sha256 == sha256 && prev.startLine <= startLine && prev.endLine >= endLine
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
		input: Tool.ToolInput,
		normalizedPath: Path,
		startLine: Int,
		endLine: Int,
	): Tool.ToolOutput {
		val summarizeMaxLines = s.get(ReadSettings.SummarizeMaxLinesSetting()).value
		if (endLine - startLine + 1 > summarizeMaxLines) {
			return Tool.ToolOutput(
				s.get(ReadSettings.MessageTooManyLinesSetting()).value.format(summarizeMaxLines), false
			)
		}
		val args = input.arguments
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				startLine,
				endLine,
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
		val prompt = args["prompt"]?.jsonPrimitive?.content?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
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
