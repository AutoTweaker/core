package io.github.autotweaker.core.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.core.Unicode
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.data.settings.getValue
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.tool.get
import kotlinx.serialization.json.*

@AutoService(Tool::class)
class Read(
    private val settings: List<SettingItem>
) : Tool<ReadInput, ReadOutput> {
    private inline fun <reified T : SettingItem.Value> setting(key: String): T =
        settings.getValue<T>(SettingKey(key))

    private fun str(key: String): String = setting<SettingItem.Value.ValString>(key).value
    private fun int(key: String): Int = setting<SettingItem.Value.ValInt>(key).value

    override val name: String = "read"
    override val description: String
        get() = str("core.tool.read.description")
    override val functions: List<Tool.Function>
        get() = listOf(
            Tool.Function(
                name = "file",
                description = str("core.tool.read.function.description.file")
                    .format(int("core.tool.read.setting.max.chars"), int("core.tool.read.setting.max.lines")),
                parameters = commonProperties + mapOf(
                    "line_number" to Tool.Function.Property(
                        description = str("core.tool.read.function.description.file.property.line.number"),
                        required = false,
                        value = Tool.Function.Property.Value.BooleanValue,
                    ),
                ),
            ),
            Tool.Function(
                name = "summarize",
                description = str("core.tool.read.function.description.summarize")
                    .format(
                        int("core.tool.read.function.summarize.setting.max.chars"),
                        int("core.tool.read.function.summarize.setting.min.chars"),
                        int("core.tool.read.function.summarize.setting.max.lines")
                    ),
                parameters = commonProperties + mapOf(
                    "prompt" to Tool.Function.Property(
                        description = str("core.tool.read.function.description.summarize.property.prompt"),
                        required = false,
                        value = Tool.Function.Property.Value.StringValue(),
                    ),
                ),
            ),
            Tool.Function(
                name = "unicode",
                description = str("core.tool.read.function.description.unicode")
                    .format(int("core.tool.read.function.unicode.setting.max.chars")),
                parameters = mapOf(
                    "file_path" to Tool.Function.Property(
                        description = str("core.tool.read.property.description.file.path"),
                        required = true,
                        value = Tool.Function.Property.Value.StringValue(),
                    ),
                    "max_chars" to Tool.Function.Property(
                        description = str("core.tool.read.function.description.unicode.property.max.chars")
                            .format(int("core.tool.read.function.unicode.setting.max.chars")),
                        required = true,
                        value = Tool.Function.Property.Value.IntegerValue(),
                    ),
                ),
            ),
        )

    private val commonProperties: Map<String, Tool.Function.Property>
        get() = mapOf(
            "file_path" to Tool.Function.Property(
                description = str("core.tool.read.property.description.file.path"),
                required = true,
                value = Tool.Function.Property.Value.StringValue(),
            ),
            "start_line" to Tool.Function.Property(
                description = str("core.tool.read.property.description.start.line"),
                required = true,
                value = Tool.Function.Property.Value.IntegerValue(),
            ),
            "end_line" to Tool.Function.Property(
                description = str("core.tool.read.property.description.end.line"),
                required = true,
                value = Tool.Function.Property.Value.IntegerValue(),
            ),
        )

    override suspend fun execute(input: ReadInput): ReadOutput {
        val args = input.arguments
        val functionName = args["function_name"]!!.jsonPrimitive.content
        val filePath = args["file_path"]!!.jsonPrimitive.content
        val fs = input.provider.get<FileSystemService>()
        val normalizedPath = try {
            fs.normalize(filePath)
        } catch (e: Exception) {
            return ReadOutput(str("core.tool.message.path.error"), false)
        }

        if (!fs.exists(normalizedPath)) {
            return ReadOutput(str("core.tool.read.message.error.file.not.found"), false)
        }
        if (!fs.isRegularFile(normalizedPath)) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        return when (functionName) {
            "file", "summarize" -> {
                val startLine = args["start_line"]!!.jsonPrimitive.int
                val endLine = args["end_line"]!!.jsonPrimitive.int

                if (startLine < 1) {
                    return ReadOutput(str("core.tool.read.message.error.start.line"), false)
                }
                if (endLine < startLine) {
                    return ReadOutput(str("core.tool.read.message.error.start.line.bigger.than.end.line"), false)
                }

                when (functionName) {
                    "file" -> executeFile(input, fs, normalizedPath, startLine, endLine)
                    "summarize" -> executeSummarize(input, fs, normalizedPath, startLine, endLine)
                    else -> throw IllegalArgumentException("Unknown function: $functionName")
                }
            }

            "unicode" -> {
                val defaultMaxChars = int("core.tool.read.function.unicode.setting.max.chars")
                val maxChars = args["max_chars"]!!.jsonPrimitive.int
                if (maxChars > defaultMaxChars) {
                    return ReadOutput(
                        str("core.tool.read.message.error.too.many.chars").format(defaultMaxChars), false
                    )
                }
                executeUnicode(fs, normalizedPath, maxChars)
            }

            else -> throw IllegalArgumentException("Unknown function: $functionName")
        }
    }

    //read_file
    private suspend fun executeFile(
        input: ReadInput,
        fs: FileSystemService,
        normalizedPath: java.nio.file.Path,
        startLine: Int,
        endLine: Int,
    ): ReadOutput {
        val maxLines = int("core.tool.read.function.file.setting.max.lines")
        if (endLine - startLine + 1 > maxLines) {
            return ReadOutput(str("core.tool.read.message.error.too.many.lines").format(maxLines), false)
        }

        val args = input.arguments
        val lineNumber = args["line_number"]?.jsonPrimitive?.booleanOrNull ?: true
        val content = try {
            readFileContent(
                fs,
                normalizedPath,
                startLine,
                endLine,
                maxChars = int("core.tool.read.function.file.setting.max.chars"),
                truncateMessage = str("core.tool.read.function.message.file.truncate"),
                lineNumber = lineNumber,
            )
        } catch (e: IllegalStateException) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        //计算SHA256
        val sha256 = try {
            fs.sha256(normalizedPath)
        } catch (e: Exception) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        //重复读取检测
        for (prev in input.previousReads) {
            if (prev.filePath == normalizedPath
                && prev.fileSha256 == sha256
                && startLine >= prev.startLine
                && endLine <= prev.endLine
            ) {
                return ReadOutput(str("core.tool.read.message.duplicate"), true)
            }
        }

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
        val maxLines = int("core.tool.read.function.summarize.setting.max.lines")
        if (endLine - startLine + 1 > maxLines) {
            return ReadOutput(str("core.tool.read.message.error.too.many.lines").format(maxLines), false)
        }

        val maxChars = int("core.tool.read.function.summarize.setting.max.chars")
        val minChars = int("core.tool.read.function.summarize.setting.min.chars")
        val args = input.arguments

        //读取内容
        val content = try {
            readFileContent(
                fs,
                normalizedPath,
                startLine,
                endLine,
                maxChars = int("core.tool.read.function.summarize.setting.max.chars"),
                truncateMessage = str("core.tool.read.function.message.summarize.input.truncate"),
                lineNumber = false,
            )
        } catch (e: IllegalStateException) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        //文件太小，无法总结
        if (content.length < minChars) {
            return ReadOutput(
                str("core.tool.read.function.message.error.summarize.too.small").format(content.length, minChars),
                false
            )
        }

        val defaultPrompt = str("core.tool.read.summarize.prompt")
        val prompt = args["prompt"]?.jsonPrimitive?.content?.let { "$defaultPrompt\n$it" } ?: defaultPrompt

        val summarizeService = input.provider.get<SummarizeService>()
        val output = try {
            summarizeService.summarize(content, prompt)
        } catch (e: Exception) {
            return ReadOutput(str("core.tool.read.function.message.error.summarize.failed"), false)
        }

        val truncateMsg = str("core.tool.read.function.message.summarize.output.truncate")
        return if (output.length > maxChars) {
            ReadOutput(output.take(maxChars) + truncateMsg.format(output.length), true)
        } else {
            ReadOutput(output, true)
        }
    }

    //read_unicode
    private suspend fun executeUnicode(
        fs: FileSystemService,
        normalizedPath: java.nio.file.Path,
        maxChars: Int,
    ): ReadOutput {
        //读取内容
        val allUnicode: List<Unicode> = try {
            fs.readUnicode(normalizedPath)
        } catch (e: Exception) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        if (allUnicode.size > maxChars) {
            return ReadOutput(
                str("core.tool.read.function.message.error.unicode.too.many.chars").format(maxChars), false
            )
        }

        return ReadOutput(allUnicode.joinToString("") { it.value }, true)
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
            throw IllegalStateException("Failed to read", e)
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
    fun readAllBytes(path: java.nio.file.Path): ByteArray
    fun sha256(path: java.nio.file.Path): String
}

interface SummarizeService {
    suspend fun summarize(content: String, prompt: String): String
}
