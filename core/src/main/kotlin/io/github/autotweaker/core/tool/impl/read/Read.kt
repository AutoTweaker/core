package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.Unicode
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.tool.get
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

@Suppress("unused")
class Read(
	settings: List<SettingItem>
) : Tool<ReadInput, ReadOutput> {
	private val summarizePrompt: String = settings.find("core.tool.read.summarize.prompt")
	
	//描述
	override val description: String = settings.find("core.tool.read.description")
	private val descriptionFilePath: String = settings.find("core.tool.read.property.description.file.path")
	private val descriptionStartLine: String = settings.find("core.tool.read.property.description.start.line")
	private val descriptionEndLine: String = settings.find("core.tool.read.property.description.end.line")
	
	//function描述
	private val fileDescription: String = settings.find("core.tool.read.function.description.file")
	private val fileDescriptionLineNumber: String =
		settings.find("core.tool.read.function.description.file.property.line.number")
	
	private val summarizeDescription: String = settings.find("core.tool.read.function.description.summarize")
	private val summarizeDescriptionPrompt: String =
		settings.find("core.tool.read.function.description.summarize.property.prompt")
	
	private val unicodeDescription: String = settings.find("core.tool.read.function.description.unicode")
	private val unicodeDescriptionMaxChars: String =
		settings.find("core.tool.read.function.description.unicode.property.max.chars")
	
	//数值
	private val fileMaxChars: Int = settings.find("core.tool.read.function.file.setting.max.chars")
	private val fileMaxLines: Int = settings.find("core.tool.read.function.file.setting.max.lines")
	
	private val summarizeMaxInputChars: Int = settings.find("core.tool.read.function.summarize.setting.max.input.chars")
	private val summarizeMaxOutputChars: Int =
		settings.find("core.tool.read.function.summarize.setting.max.output.chars")
	private val summarizeMinChars: Int = settings.find("core.tool.read.function.summarize.setting.min.chars")
	private val summarizeMaxLines: Int = settings.find("core.tool.read.function.summarize.setting.max.lines")
	
	private val unicodeMaxChars: Int = settings.find("core.tool.read.function.unicode.setting.max.chars")
	
	//工具消息
	private val messagePathError: String = settings.find("core.tool.message.path.error")
	private val messageNotFound: String = settings.find("core.tool.read.message.error.file.not.found")
	private val messageCanNotRead: String = settings.find("core.tool.read.message.error.file.can.not.read")
	private val messageStartLineError: String = settings.find("core.tool.read.message.error.start.line")
	private val messageSartBiggerThanEnd: String =
		settings.find("core.tool.read.message.error.start.line.bigger.than.end.line")
	private val messageTooManyLInes: String = settings.find("core.tool.read.message.error.too.many.lines")
	
	//function消息
	private val unicodeMessageTooManyChars: String =
		settings.find("core.tool.read.function.message.error.unicode.too.many.chars")
	
	private val fileMessageTruncate: String = settings.find("core.tool.read.function.message.file.truncate")
	private val fileMessageDuplicate: String = settings.find("core.tool.read.function.message.error.file.duplicate")
	
	private val summarizeMessageInputTruncate: String =
		settings.find("core.tool.read.function.message.summarize.input.truncate")
	private val summarizeMessageOutputTruncate: String =
		settings.find("core.tool.read.function.message.summarize.output.truncate")
	private val summarizeMessageTooFew: String =
		settings.find("core.tool.read.function.message.error.summarize.too.few")
	private val summarizeMessageFailed: String = settings.find("core.tool.read.function.message.error.summarize.failed")
	
	//工具属性
	override val name: String = "read"
	override val functions: List<Tool.Function>
		get() = listOf(
			Tool.Function(
				name = "file",
				description = fileDescription
					.format(fileMaxChars, fileMaxLines),
				parameters = commonProperties + mapOf(
					"line_number" to Tool.Function.Property(
						description = fileDescriptionLineNumber,
						required = false,
						value = Tool.Function.Property.Value.BooleanValue,
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
						value = Tool.Function.Property.Value.StringValue(),
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
						value = Tool.Function.Property.Value.StringValue(),
					),
					"max_chars" to Tool.Function.Property(
						description = unicodeDescriptionMaxChars.format(unicodeMaxChars),
						required = true,
						value = Tool.Function.Property.Value.IntegerValue(),
					),
				),
			),
		)
	
	private val commonProperties: Map<String, Tool.Function.Property>
		get() = mapOf(
			"file_path" to Tool.Function.Property(
				description = descriptionFilePath,
				required = true,
				value = Tool.Function.Property.Value.StringValue(),
			),
			"start_line" to Tool.Function.Property(
				description = descriptionStartLine,
				required = true,
				value = Tool.Function.Property.Value.IntegerValue(),
			),
			"end_line" to Tool.Function.Property(
				description = descriptionEndLine,
				required = true,
				value = Tool.Function.Property.Value.IntegerValue(),
			),
		)
	
	//入口方法
	override suspend fun execute(input: ReadInput): ReadOutput {
		val args = input.arguments
		val functionName = args["function_name"]!!.jsonPrimitive.content
		val filePath = args["file_path"]!!.jsonPrimitive.content
		val fs = input.provider.get<FileSystemService>()
		//解析路径
		val normalizedPath = try {
			fs.normalize(filePath)
		} catch (_: Exception) {
			return ReadOutput(messagePathError, false)
		}
		//检测是否存在
		if (!fs.exists(normalizedPath)) {
			return ReadOutput(messageNotFound, false)
		}
		//检查是否可解析
		if (!fs.isRegularFile(normalizedPath)) {
			return ReadOutput(messageCanNotRead, false)
		}
		
		//调用对应function
		return when (functionName) {
			//file或summarize解析行号
			"file", "summarize" -> {
				val startLine = args["start_line"]!!.jsonPrimitive.int
				val endLine = args["end_line"]!!.jsonPrimitive.int
				
				if (startLine < 1) {
					return ReadOutput(messageStartLineError, false)
				}
				if (endLine < startLine) {
					return ReadOutput(messageSartBiggerThanEnd, false)
				}
				
				when (functionName) {
					"file" -> executeFile(input, fs, normalizedPath, startLine, endLine)
					"summarize" -> executeSummarize(input, fs, normalizedPath, startLine, endLine)
					else -> throw IllegalArgumentException("Unknown function: $functionName")
				}
			}
			
			//unicode解析字符数
			"unicode" -> {
				val maxChars = args["max_chars"]!!.jsonPrimitive.int
				//校验字符数参数
				if (maxChars > unicodeMaxChars) {
					return ReadOutput(
						unicodeMessageTooManyChars.format(unicodeMaxChars),
						false
					)
				}
				executeUnicode(fs, normalizedPath, maxChars)
			}
			
			else -> throw IllegalArgumentException("Unknown function: $functionName")
		}
	}
	
	//read_file
	private fun executeFile(
		input: ReadInput,
		fs: FileSystemService,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ReadOutput {
		//检查行数
		if (endLine - startLine + 1 > fileMaxLines) {
			return ReadOutput(messageTooManyLInes.format(fileMaxLines), false)
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
				maxChars = fileMaxChars,
				truncateMessage = fileMessageTruncate,
				lineNumber = lineNumber,
			)
		} catch (_: IllegalStateException) {
			return ReadOutput(messageCanNotRead, false)
		}
		
		//计算SHA256
		val sha256 = try {
			fs.sha256(normalizedPath)
		} catch (_: Exception) {
			return ReadOutput(messageCanNotRead, false)
		}
		
		//重复读取检测
		for (prev in input.previousReads) {
			if (prev.filePath == normalizedPath
				&& prev.fileSha256 == sha256
				&& startLine >= prev.startLine
				&& endLine <= prev.endLine
			) {
				return ReadOutput(fileMessageDuplicate, true)
			}
		}
		
		//返回SHA256和文件内容
		return ReadOutput("$sha256\n$content", true)
	}
	
	//read_summarize
	private suspend fun executeSummarize(
		input: ReadInput,
		fs: FileSystemService,
		normalizedPath: java.nio.file.Path,
		startLine: Int,
		endLine: Int,
	): ReadOutput {
		//检查行数
		if (endLine - startLine + 1 > summarizeMaxLines) {
			return ReadOutput(messageTooManyLInes.format(summarizeMaxLines), false)
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
				maxChars = summarizeMaxInputChars,
				truncateMessage = summarizeMessageInputTruncate,
				lineNumber = false,
			)
		} catch (_: IllegalStateException) {
			return ReadOutput(messageCanNotRead, false)
		}
		
		//文件太小，无法总结
		if (content.length < summarizeMinChars) {
			return ReadOutput(
				summarizeMessageTooFew.format(content.length, summarizeMinChars),
				false
			)
		}
		
		//构建系统提示
		val prompt = args["prompt"]?.jsonPrimitive?.content?.let { "$summarizePrompt\n$it" } ?: summarizePrompt
		
		//获取总结器
		val summarizeService = input.provider.get<SummarizeService>()
		
		//总结
		val output = try {
			summarizeService.summarize(content, prompt)
		} catch (e: Exception) {
			return ReadOutput(summarizeMessageFailed.format(e.message), false)
		}
		//截断
		return if (output.length > summarizeMaxOutputChars) {
			ReadOutput(
				output.take(summarizeMaxOutputChars) +
						summarizeMessageOutputTruncate.format(output.length),
				true
			)
		} else {
			ReadOutput(output, true)
		}
	}
	
	//read_unicode
	private fun executeUnicode(
		fs: FileSystemService,
		normalizedPath: java.nio.file.Path,
		maxChars: Int,
	): ReadOutput {
		//读取内容
		val allUnicode: List<Unicode> = try {
			fs.readUnicode(normalizedPath)
		} catch (_: Exception) {
			return ReadOutput(messageCanNotRead, false)
		}
		
		return ReadOutput(
			allUnicode.take(maxChars).joinToString("") { it.value },
			true
		)
	}
	
	//按照行号读取文件并处理截断
	private fun readFileContent(
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

interface FileSystemService {
	fun normalize(filePath: String): java.nio.file.Path
	fun exists(path: java.nio.file.Path): Boolean
	fun isRegularFile(path: java.nio.file.Path): Boolean
	fun readUnicode(path: java.nio.file.Path): List<Unicode>
	fun readAllLines(path: java.nio.file.Path): List<String>
	fun sha256(path: java.nio.file.Path): String
}

interface SummarizeService {
	suspend fun summarize(content: String, prompt: String): String
}
