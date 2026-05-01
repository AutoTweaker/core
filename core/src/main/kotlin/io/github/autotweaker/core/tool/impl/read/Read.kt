package io.github.autotweaker.core.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.core.Unicode
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.tool.Tool.ToolInput
import io.github.autotweaker.core.tool.Tool.ToolOutput
import io.github.autotweaker.core.tool.get
import kotlinx.serialization.json.*

@AutoService(Tool::class)
class Read : Tool {
	
	private data class Runtime(
		//数值
		val fileMaxChars: Int,
		val fileMaxLines: Int,
		val summarizeMaxInputChars: Int,
		val summarizeMaxOutputChars: Int,
		val summarizeMinChars: Int,
		val summarizeMaxLines: Int,
		val unicodeMaxChars: Int,
		//提示
		val summarizePrompt: String,
		//工具消息
		val messagePathError: String,
		val messageNotFound: String,
		val messageCanNotRead: String,
		val messageStartLineError: String,
		val messageStartBiggerThanEnd: String,
		val messageTooManyLines: String,
		//function消息
		val unicodeMessageTooManyChars: String,
		val fileMessageTruncate: String,
		val fileMessageDuplicate: String,
		val summarizeMessageInputTruncate: String,
		val summarizeMessageOutputTruncate: String,
		val summarizeMessageTooFew: String,
		val summarizeMessageFailed: String,
	)
	
	override fun resolveMeta(settings: List<SettingItem>): Tool.Meta {
		val description: String = settings.find("core.tool.read.description")
		val descriptionFilePath: String = settings.find("core.tool.read.property.description.file.path")
		val descriptionStartLine: String = settings.find("core.tool.read.property.description.start.line")
		val descriptionEndLine: String = settings.find("core.tool.read.property.description.end.line")
		
		val fileDescription: String = settings.find("core.tool.read.function.description.file")
		val fileDescriptionLineNumber: String =
			settings.find("core.tool.read.function.description.file.property.line.number")
		
		val summarizeDescription: String = settings.find("core.tool.read.function.description.summarize")
		val summarizeDescriptionPrompt: String =
			settings.find("core.tool.read.function.description.summarize.property.prompt")
		
		val unicodeDescription: String = settings.find("core.tool.read.function.description.unicode")
		val unicodeDescriptionMaxChars: String =
			settings.find("core.tool.read.function.description.unicode.property.max.chars")
		
		val fileMaxChars: Int = settings.find("core.tool.read.function.file.setting.max.chars")
		val fileMaxLines: Int = settings.find("core.tool.read.function.file.setting.max.lines")
		
		val summarizeMaxInputChars: Int = settings.find("core.tool.read.function.summarize.setting.max.input.chars")
		val summarizeMinChars: Int = settings.find("core.tool.read.function.summarize.setting.min.chars")
		val summarizeMaxLines: Int = settings.find("core.tool.read.function.summarize.setting.max.lines")
		
		val unicodeMaxChars: Int = settings.find("core.tool.read.function.unicode.setting.max.chars")
		
		val commonProperties: Map<String, Tool.Function.Property> = mapOf(
			"file_path" to Tool.Function.Property(
				description = descriptionFilePath,
				required = true,
				valueType = Tool.Function.Property.ValueType.StringValue(),
			),
			"start_line" to Tool.Function.Property(
				description = descriptionStartLine,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
			"end_line" to Tool.Function.Property(
				description = descriptionEndLine,
				required = true,
				valueType = Tool.Function.Property.ValueType.IntegerValue(),
			),
		)
		
		return Tool.Meta(
			name = "read",
			description = description,
			functions = listOf(
				Tool.Function(
					name = "file",
					description = fileDescription
						.format(fileMaxChars, fileMaxLines),
					parameters = commonProperties + mapOf(
						"line_number" to Tool.Function.Property(
							description = fileDescriptionLineNumber,
							required = false,
							valueType = Tool.Function.Property.ValueType.BooleanValue,
						),
					),
				),
				Tool.Function(
					name = "summarize",
					description = summarizeDescription
						.format(summarizeMaxInputChars, summarizeMinChars, summarizeMaxLines),
					parameters = commonProperties + mapOf(
						"prompt" to Tool.Function.Property(
							description = summarizeDescriptionPrompt,
							required = false,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
					),
				),
				Tool.Function(
					name = "unicode",
					description = unicodeDescription,
					parameters = mapOf(
						"file_path" to Tool.Function.Property(
							description = descriptionFilePath,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"max_chars" to Tool.Function.Property(
							description = unicodeDescriptionMaxChars.format(unicodeMaxChars),
							required = true,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
					),
				),
			)
		)
	}
	
	override suspend fun execute(input: ToolInput): ToolOutput {
		val r = Runtime(
			fileMaxChars = input.settings.find("core.tool.read.function.file.setting.max.chars"),
			fileMaxLines = input.settings.find("core.tool.read.function.file.setting.max.lines"),
			summarizeMaxInputChars = input.settings.find("core.tool.read.function.summarize.setting.max.input.chars"),
			summarizeMaxOutputChars = input.settings.find("core.tool.read.function.summarize.setting.max.output.chars"),
			summarizeMinChars = input.settings.find("core.tool.read.function.summarize.setting.min.chars"),
			summarizeMaxLines = input.settings.find("core.tool.read.function.summarize.setting.max.lines"),
			unicodeMaxChars = input.settings.find("core.tool.read.function.unicode.setting.max.chars"),
			summarizePrompt = input.settings.find("core.tool.read.summarize.prompt"),
			messagePathError = input.settings.find("core.tool.message.path.error"),
			messageNotFound = input.settings.find("core.tool.read.message.error.file.not.found"),
			messageCanNotRead = input.settings.find("core.tool.read.message.error.file.can.not.read"),
			messageStartLineError = input.settings.find("core.tool.read.message.error.start.line"),
			messageStartBiggerThanEnd = input.settings.find("core.tool.read.message.error.start.line.bigger.than.end.line"),
			messageTooManyLines = input.settings.find("core.tool.read.message.error.too.many.lines"),
			unicodeMessageTooManyChars = input.settings.find("core.tool.read.function.message.error.unicode.too.many.chars"),
			fileMessageTruncate = input.settings.find("core.tool.read.function.message.file.truncate"),
			fileMessageDuplicate = input.settings.find("core.tool.read.function.message.error.file.duplicate"),
			summarizeMessageInputTruncate = input.settings.find("core.tool.read.function.message.summarize.input.truncate"),
			summarizeMessageOutputTruncate = input.settings.find("core.tool.read.function.message.summarize.output.truncate"),
			summarizeMessageTooFew = input.settings.find("core.tool.read.function.message.error.summarize.too.few"),
			summarizeMessageFailed = input.settings.find("core.tool.read.function.message.error.summarize.failed"),
		)
		
		val args = input.arguments
		val functionName = input.functionName
		val filePath = args["file_path"]!!.jsonPrimitive.content
		val fs = input.provider.get<FileSystemService>()
		//解析路径
		val normalizedPath = try {
			fs.normalize(filePath)
		} catch (_: Exception) {
			return ToolOutput(r.messagePathError, false)
		}
		//检测是否存在
		if (!fs.exists(normalizedPath)) {
			return ToolOutput(r.messageNotFound, false)
		}
		//检查是否可解析
		if (!fs.isRegularFile(normalizedPath)) {
			return ToolOutput(r.messageCanNotRead, false)
		}
		
		//调用对应function
		return when (functionName) {
			//file或summarize解析行号
			"file", "summarize" -> {
				val startLine = args["start_line"]!!.jsonPrimitive.int
				val endLine = args["end_line"]!!.jsonPrimitive.int
				
				if (startLine < 1) {
					return ToolOutput(r.messageStartLineError, false)
				}
				if (endLine < startLine) {
					return ToolOutput(r.messageStartBiggerThanEnd, false)
				}
				
				when (functionName) {
					"file" -> executeFile(input, fs, r, normalizedPath, startLine, endLine)
					"summarize" -> executeSummarize(input, fs, r, normalizedPath, startLine, endLine)
					else -> throw IllegalArgumentException("Unknown function: $functionName")
				}
			}
			
			//unicode解析字符数
			"unicode" -> {
				val maxChars = args["max_chars"]!!.jsonPrimitive.int
				//校验字符数参数
				if (maxChars > r.unicodeMaxChars) {
					return ToolOutput(
						r.unicodeMessageTooManyChars.format(r.unicodeMaxChars),
						false
					)
				}
				executeUnicode(fs, r, normalizedPath, maxChars)
			}
			
			else -> throw IllegalArgumentException("Unknown function: $functionName")
		}
	}
	
	//read_file
	private suspend fun executeFile(
		input: ToolInput,
		fs: FileSystemService,
		r: Runtime,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ToolOutput {
		//检查行数
		if (endLine - startLine + 1 > r.fileMaxLines) {
			return ToolOutput(r.messageTooManyLines.format(r.fileMaxLines), false)
		}
		
		//参数
		val args = input.arguments
		val lineNumber = args["line_number"]?.jsonPrimitive?.booleanOrNull ?: true
		//读取
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				startLine,
				endLine,
				maxChars = r.fileMaxChars,
				truncateMessage = r.fileMessageTruncate,
				lineNumber = lineNumber,
			)
		} catch (_: IllegalStateException) {
			return ToolOutput(r.messageCanNotRead, false)
		}
		
		//计算SHA256
		val sha256 = try {
			fs.sha256(normalizedPath)
		} catch (_: Exception) {
			return ToolOutput(r.messageCanNotRead, false)
		}
		
		//提取历史Read
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
		
		//重复读取检测
		if (previousReads.any { prev ->
				try {
					//解析路径
					val prevNormalized = fs.normalize(prev.path)
					//匹配路径
					prevNormalized == normalizedPath
							//匹配sha256（判断文件是否已被修改）
							&& prev.sha256 == sha256
							//匹配范围（相同或更小）
							&& prev.startLine <= startLine
							&& prev.endLine >= endLine
				} catch (_: Exception) {
					//路径解析失败
					false
				}
			}) {
			//判定为重复
			return ToolOutput(r.fileMessageDuplicate.format(sha256), true)
		}
		
		//返回SHA256和文件内容
		return ToolOutput("$sha256\n$content", true)
	}
	
	//read_summarize
	private suspend fun executeSummarize(
		input: ToolInput,
		fs: FileSystemService,
		r: Runtime,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ToolOutput {
		//检查行数
		if (endLine - startLine + 1 > r.summarizeMaxLines) {
			return ToolOutput(r.messageTooManyLines.format(r.summarizeMaxLines), false)
		}
		
		//参数
		val args = input.arguments
		
		//读取内容
		val content = try {
			readFileContent(
				fs,
				normalizedPath,
				startLine,
				endLine,
				maxChars = r.summarizeMaxInputChars,
				truncateMessage = r.summarizeMessageInputTruncate,
				lineNumber = false,
			)
		} catch (_: IllegalStateException) {
			return ToolOutput(r.messageCanNotRead, false)
		}
		
		//文件太小，无法总结
		if (content.length < r.summarizeMinChars) {
			return ToolOutput(
				r.summarizeMessageTooFew.format(content.length, r.summarizeMinChars),
				false
			)
		}
		
		//构建系统提示
		val prompt = args["prompt"]?.jsonPrimitive?.content?.let { "${r.summarizePrompt}\n\n$it" } ?: r.summarizePrompt
		
		//获取总结器
		val summarizeService = input.provider.get<SummarizeService>()
		
		//总结
		val output = try {
			summarizeService.summarize(content, prompt)
		} catch (e: Exception) {
			return ToolOutput(r.summarizeMessageFailed.format(e.message), false)
		}
		//截断
		return if (output.length > r.summarizeMaxOutputChars) {
			ToolOutput(
				output.take(r.summarizeMaxOutputChars) +
						r.summarizeMessageOutputTruncate.format(output.length),
				true
			)
		} else {
			ToolOutput(output, true)
		}
	}
	
	//read_unicode
	private suspend fun executeUnicode(
		fs: FileSystemService,
		r: Runtime,
		normalizedPath: java.nio.file.Path,
		maxChars: Int,
	): ToolOutput {
		//读取内容
		val allUnicode: List<Unicode> = try {
			fs.readUnicode(normalizedPath)
		} catch (_: Exception) {
			return ToolOutput(r.messageCanNotRead, false)
		}
		
		return ToolOutput(
			allUnicode.take(maxChars).joinToString("") { it.value },
			true
		)
	}
	
	//按照行号读取文件并处理截断
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
