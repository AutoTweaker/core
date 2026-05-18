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

package io.github.autotweaker.core.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.tool.Tool.ToolInput
import io.github.autotweaker.core.tool.Tool.ToolOutput
import io.github.autotweaker.core.tool.get
import io.github.autotweaker.core.tool.impl.ToolSettings
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

@AutoService(Tool::class)
class Read : Tool {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	override fun resolveMeta(service: SettingService): Tool.Meta {
		val commonProperties: Map<String, Tool.Function.Property> = mapOf(
			"file_path" to Tool.Function.Property(
				description = service.get(ReadSettings.FilePathPropDescriptionSetting).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.StringValue(),
			),
			"start_line" to Tool.Function.Property(
				description = service.get(ReadSettings.StartLinePropDescriptionSetting).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
			"end_line" to Tool.Function.Property(
				description = service.get(ReadSettings.EndLinePropDescriptionSetting).value,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
		)
		
		return Tool.Meta(
			name = "read", description = service.get(ReadSettings.DescriptionSetting).value, functions = listOf(
				Tool.Function(
					name = "file",
					description = service.get(ReadSettings.FileFuncDescriptionSetting).value.format(
						service.get(ReadSettings.FileMaxCharsSetting).value,
						service.get(ReadSettings.FileMaxLinesSetting).value
					),
					parameters = commonProperties + mapOf(
						"line_number" to Tool.Function.Property(
							description = service.get(ReadSettings.LineNumberPropDescriptionSetting).value,
							required = false,
							valueType = Tool.Function.Property.ValueType.BooleanValue,
						),
					),
				),
				Tool.Function(
					name = "summarize",
					description = service.get(ReadSettings.SummarizeFuncDescriptionSetting).value.format(
						service.get(ReadSettings.SummarizeMaxInputCharsSetting).value,
						service.get(ReadSettings.SummarizeMinCharsSetting).value,
						service.get(ReadSettings.SummarizeMaxLinesSetting).value
					),
					parameters = commonProperties + mapOf(
						"prompt" to Tool.Function.Property(
							description = service.get(ReadSettings.SummarizePromptPropDescriptionSetting).value,
							required = false,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
					),
				),
				Tool.Function(
					name = "unicode",
					description = service.get(ReadSettings.UnicodeFuncDescriptionSetting).value,
					parameters = mapOf(
						"file_path" to Tool.Function.Property(
							description = service.get(ReadSettings.FilePathPropDescriptionSetting).value,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"max_chars" to Tool.Function.Property(
							description = service.get(ReadSettings.UnicodeMaxCharsPropDescriptionSetting).value.format(
								service.get(ReadSettings.UnicodeMaxCharsSetting).value
							),
							required = true,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
					),
				),
			)
		)
	}
	
	override suspend fun execute(input: ToolInput): ToolOutput {
		val s = input.service
		logger.debug(
			"Read tool started  tool=read  function={}  filePath={}",
			input.functionName,
			input.arguments["file_path"]?.jsonPrimitive?.content
		)
		val args = input.arguments
		val functionName = input.functionName
		val filePath = args["file_path"]!!.jsonPrimitive.content
		val fs = input.provider.get<FileSystemService>()
		val normalizedPath = try {
			fs.normalize(filePath)
		} catch (_: Exception) {
			return ToolOutput(s.get(ToolSettings.PathErrorMessage).value, false)
		}
		if (!fs.exists(normalizedPath)) {
			return ToolOutput(s.get(ReadSettings.MessageFileNotFoundSetting).value, false)
		}
		if (!fs.isRegularFile(normalizedPath)) {
			return ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting).value, false)
		}
		
		return when (functionName) {
			"file", "summarize" -> {
				val startLine = args["start_line"]!!.jsonPrimitive.int
				val endLine = args["end_line"]!!.jsonPrimitive.int
				if (startLine < 1) {
					return ToolOutput(s.get(ReadSettings.MessageStartLineErrorSetting).value, false)
				}
				if (endLine < startLine) {
					return ToolOutput(s.get(ReadSettings.MessageStartLineBiggerThanEndSetting).value, false)
				}
				if (functionName == "file") {
					executeFile(input, fs, s, normalizedPath, startLine, endLine)
				} else {
					executeSummarize(input, fs, s, normalizedPath, startLine, endLine)
				}
			}
			
			"unicode" -> {
				val maxChars = args["max_chars"]!!.jsonPrimitive.int
				val unicodeMaxChars = s.get(ReadSettings.UnicodeMaxCharsSetting).value
				if (maxChars > unicodeMaxChars) {
					return ToolOutput(
						s.get(ReadSettings.UnicodeMessageTooManyCharsSetting).value.format(unicodeMaxChars), false
					)
				}
				executeUnicode(fs, s, normalizedPath, maxChars)
			}
			
			else -> throw IllegalArgumentException("Unknown function: $functionName")
		}
	}
	
	private suspend fun executeFile(
		input: ToolInput,
		fs: FileSystemService,
		s: SettingService,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ToolOutput {
		val fileMaxLines = s.get(ReadSettings.FileMaxLinesSetting).value
		if (endLine - startLine + 1 > fileMaxLines) {
			return ToolOutput(
				s.get(ReadSettings.MessageTooManyLinesSetting).value.format(fileMaxLines), false
			)
		}
		
		val args = input.arguments
		val lineNumber = args["line_number"]?.jsonPrimitive?.booleanOrNull ?: true
		val content = try {
			readFileContent(
				fs, normalizedPath, startLine, endLine,
				maxChars = s.get(ReadSettings.FileMaxCharsSetting).value,
				truncateMessage = s.get(ReadSettings.FileMessageTruncateSetting).value,
				lineNumber = lineNumber,
			)
		} catch (_: IllegalStateException) {
			return ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting).value, false)
		}
		
		val sha256 = try {
			fs.sha256(normalizedPath)
		} catch (_: Exception) {
			return ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting).value, false)
		}
		
		val previousReads = buildList {
			data class PrevRead(val path: String, val sha256: String, val startLine: Int, val endLine: Int)
			
			for (entry in input.provider.get<ToolCallHistory>().getAll()) {
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
			return ToolOutput(
				s.get(ReadSettings.FileMessageDuplicateSetting).value.format(sha256), true
			)
		}
		
		return ToolOutput("$sha256\n$content", true)
	}
	
	private suspend fun executeSummarize(
		input: ToolInput,
		fs: FileSystemService,
		s: SettingService,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ToolOutput {
		val summarizeMaxLines = s.get(ReadSettings.SummarizeMaxLinesSetting).value
		if (endLine - startLine + 1 > summarizeMaxLines) {
			return ToolOutput(
				s.get(ReadSettings.MessageTooManyLinesSetting).value.format(summarizeMaxLines), false
			)
		}
		
		val args = input.arguments
		
		val content = try {
			readFileContent(
				fs, normalizedPath, startLine, endLine,
				maxChars = s.get(ReadSettings.SummarizeMaxInputCharsSetting).value,
				truncateMessage = s.get(ReadSettings.SummarizeMessageInputTruncateSetting).value,
				lineNumber = false,
			)
		} catch (_: IllegalStateException) {
			return ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting).value, false)
		}
		
		val summarizeMinChars = s.get(ReadSettings.SummarizeMinCharsSetting).value
		if (content.length < summarizeMinChars) {
			return ToolOutput(
				s.get(ReadSettings.SummarizeMessageTooFewSetting).value.format(content.length, summarizeMinChars), false
			)
		}
		
		val summarizePrompt = s.get(ReadSettings.SummarizePromptSetting).value
		val prompt = args["prompt"]?.jsonPrimitive?.content?.let { "$summarizePrompt\n\n$it" } ?: summarizePrompt
		
		val summarizeService = input.provider.get<SummarizeService>()
		
		val output = try {
			summarizeService.summarize(content, prompt)
		} catch (e: Exception) {
			return ToolOutput(
				s.get(ReadSettings.SummarizeMessageFailedSetting).value.format(e.message), false
			)
		}
		
		val summarizeMaxOutputChars = s.get(ReadSettings.SummarizeMaxOutputCharsSetting).value
		return if (output.length > summarizeMaxOutputChars) {
			ToolOutput(
				output.take(summarizeMaxOutputChars) + s.get(ReadSettings.SummarizeMessageOutputTruncateSetting).value.format(
					output.length
				), true
			)
		} else {
			ToolOutput(output, true)
		}
	}
	
	private suspend fun executeUnicode(
		fs: FileSystemService,
		s: SettingService,
		normalizedPath: java.nio.file.Path,
		maxChars: Int,
	): ToolOutput {
		val allUnicode: List<Unicode> = try {
			fs.readUnicode(normalizedPath)
		} catch (_: Exception) {
			return ToolOutput(s.get(ReadSettings.MessageFileCannotReadSetting).value, false)
		}
		
		return ToolOutput(
			allUnicode.take(maxChars).joinToString("") { it.value }, true
		)
	}
	
	private suspend fun readFileContent(
		fs: FileSystemService,
		path: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
		maxChars: Int,
		truncateMessage: String,
		lineNumber: Boolean,
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
