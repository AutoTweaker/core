package io.github.autotweaker.core.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.data.settings.getValue
import io.github.autotweaker.core.tool.Tool
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

@AutoService(Tool::class)
class Read(
    private val settings: List<SettingItem>
) : Tool<ReadInput, ReadOutput> {

    // ── Setting 便捷访问 ──────────────────────────────────────────────

    private inline fun <reified T : SettingItem.Value> setting(key: String): T =
        settings.getValue<T>(SettingKey(key))

    private fun str(key: String): String = setting<SettingItem.Value.ValString>(key).value
    private fun int(key: String): Int = setting<SettingItem.Value.ValInt>(key).value

    // ── Tool 基本属性 ─────────────────────────────────────────────────

    override val name: String = "read"

    override val description: String
        get() = str("core.tool.read.description")

    override val functions: List<Tool.Function>
        get() = listOf(
            Tool.Function(
                name = "file",
                description = str("core.tool.read.function.description.file")
                    .format(int("core.tool.read.setting.max.chars"), int("core.tool.read.setting.max.lines")),
                parameters = commonProperties,
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
                parameters = commonProperties,
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

    // ── 执行入口 ──────────────────────────────────────────────────────

    override suspend fun execute(input: ReadInput): ReadOutput {
        val functionName = input.arguments["function_name"]?.jsonPrimitive?.content ?: "file"
        return when (functionName) {
            "file" -> executeFile(input)
            "summarize" -> executeSummarize(input)
            "unicode" -> executeUnicode(input)
            else -> ReadOutput("Unknown function: $functionName", false)
        }
    }

    // ── file 函数 ─────────────────────────────────────────────────────

    private suspend fun executeFile(input: ReadInput): ReadOutput {
        val result = readFileContent(
            input,
            maxLinesKey = "core.tool.read.setting.max.lines",
            maxCharsKey = "core.tool.read.setting.max.chars",
            truncateKey = "core.tool.read.function.message.file.truncate",
        ) ?: return ReadOutput(
            str("core.tool.read.message.error.file.can.not.read"),
            false
        )

        val (content, sha256, normalizedPath, startLine, endLine) = result

        // 重复读取检测：文件未变更且读取范围相同或更小
        for (prev in input.previousReads) {
            if (prev.filePath == normalizedPath.toString()
                && prev.fileSha256 == sha256
                && startLine >= prev.startLine
                && endLine <= prev.endLine
            ) {
                return ReadOutput(str("core.tool.read.message.duplicate"), true)
            }
        }

        // 第一行为SHA256，第二行开始是文件内容
        return ReadOutput("$sha256\n$content", true)
    }

    // ── summarize 函数 ────────────────────────────────────────────────

    private suspend fun executeSummarize(input: ReadInput): ReadOutput {
        val maxChars = int("core.tool.read.function.summarize.setting.max.chars")
        val minChars = int("core.tool.read.function.summarize.setting.min.chars")

        val result = readFileContent(
            input,
            maxLinesKey = "core.tool.read.function.summarize.setting.max.lines",
            maxCharsKey = "core.tool.read.function.summarize.setting.max.chars",
            truncateKey = "core.tool.read.function.message.summarize.input.truncate",
        ) ?: return ReadOutput(
            str("core.tool.read.message.error.file.can.not.read"),
            false
        )

        val (content, _, _, _, _) = result

        // 文件太小，无需总结，直接返回内容
        if (content.length < minChars) {
            return ReadOutput(content, true)
        }

        val prompt = input.arguments["prompt"]?.jsonPrimitive?.content
            ?: "请总结这个文件的内容"

        val systemPrompt = str("core.tool.read.summarize.prompt")

        // TODO: 接入 LLM 进行总结（当前 LlmChatService 尚未实现）
        // val llmChat = input.provider.get<LlmChatService>()
        // val chatRequest = ChatRequest(
        //     model = "",
        //     messages = listOf(
        //         ChatMessage.SystemMessage(content = "$systemPrompt\n\n$prompt", createdAt = Clock.System.now()),
        //         ChatMessage.UserMessage(content = content, createdAt = Clock.System.now()),
        //     ),
        //     thinking = false,
        // )
        // var summary: String? = null
        // try {
        //     llmChat.chat(chatRequest).collect { r ->
        //         val msg = r.message as? ChatMessage.AssistantMessage
        //         if (msg?.content != null) summary = msg.content
        //     }
        // } catch (e: Exception) {
        //     return ReadOutput("Summarize failed: ${e.message}", false)
        // }
        // val output = summary ?: ""

        // 临时回退：直接返回截断内容
        val output = content

        val truncateMsg = str("core.tool.read.function.message.summarize.output.truncate")
        return if (output.length > maxChars) {
            ReadOutput(output.take(maxChars) + "\n" + truncateMsg.format(output.length), true)
        } else {
            ReadOutput(output, true)
        }
    }

    // ── unicode 函数 ──────────────────────────────────────────────────

    private suspend fun executeUnicode(input: ReadInput): ReadOutput {
        val args = input.arguments
        val filePath = args["file_path"]?.jsonPrimitive?.content
            ?: return ReadOutput(
                str("core.agent.tool.response.property.missing").format("read", "file_path"),
                false
            )
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull
            ?: return ReadOutput(
                str("core.agent.tool.response.property.error").format("read", "start_line", "integer"),
                false
            )
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull
            ?: return ReadOutput(
                str("core.agent.tool.response.property.error").format("read", "end_line", "integer"),
                false
            )

        if (startLine < 1) {
            return ReadOutput(str("core.tool.read.message.error.start.line"), false)
        }
        if (endLine < startLine) {
            return ReadOutput(str("core.tool.read.message.error.end.line"), false)
        }

        val maxChars = int("core.tool.read.function.unicode.setting.max.chars")

        val path = Path(filePath)
        if (!path.isAbsolute) {
            return ReadOutput(str("core.tool.read.message.error.file.not.found"), false)
        }

        val normalizedPath = path.normalize()
        if (!normalizedPath.exists()) {
            return ReadOutput(str("core.tool.read.message.error.file.not.found"), false)
        }
        if (!normalizedPath.isRegularFile()) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        val allLines: List<String> = try {
            Files.readAllLines(normalizedPath)
        } catch (e: Exception) {
            return ReadOutput(str("core.tool.read.message.error.file.can.not.read"), false)
        }

        val totalLines = allLines.size
        if (startLine > totalLines) {
            return ReadOutput(str("core.tool.read.message.error.start.line"), false)
        }

        val actualEndLine = minOf(endLine, totalLines)
        val selectedLines = allLines.subList(startLine - 1, actualEndLine)

        val sb = StringBuilder()
        for (i in selectedLines.indices) {
            val lineNum = startLine + i
            sb.appendLine("Line $lineNum:")
            for (ch in selectedLines[i]) {
                sb.append("U+%04X ".format(ch.code))
            }
            sb.appendLine()

            if (sb.length > maxChars) {
                sb.appendLine(
                    str("core.tool.read.function.message.error.unicode.too.many.chars").format(maxChars)
                )
                break
            }
        }

        return ReadOutput(sb.toString().trimEnd(), true)
    }

    // ── 文件读取核心 ──────────────────────────────────────────────────

    /**
     * 读取文件内容，返回 (内容, SHA256, 规范化路径, startLine, endLine) 或 null。
     *
     * null 表示参数缺失、文件不存在、或行数超限等不可恢复的错误。
     * 截断属于正常行为，会在内容末尾追加截断提示。
     */
    private fun readFileContent(
        input: ReadInput,
        maxLinesKey: String,
        maxCharsKey: String,
        truncateKey: String,
    ): FileReadResult? {
        val args = input.arguments
        val filePath = args["file_path"]?.jsonPrimitive?.content ?: return null
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull ?: return null
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull ?: return null

        if (startLine !in 1..endLine) return null

        val maxLines = int(maxLinesKey)
        val maxChars = int(maxCharsKey)

        val requestedLines = endLine - startLine + 1
        if (requestedLines > maxLines) return null

        val path = Path(filePath)
        if (!path.isAbsolute) return null

        val normalizedPath = path.normalize()
        if (!normalizedPath.exists() || !normalizedPath.isRegularFile()) return null

        val allLines: List<String> = try {
            Files.readAllLines(normalizedPath)
        } catch (e: Exception) {
            return null
        }

        val fileBytes = try {
            Files.readAllBytes(normalizedPath)
        } catch (e: Exception) {
            return null
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

        val totalLines = allLines.size
        if (startLine > totalLines) return null

        val actualEndLine = minOf(endLine, totalLines)
        val selectedLines = allLines.subList(startLine - 1, actualEndLine)

        val sb = StringBuilder()
        for (i in selectedLines.indices) {
            val lineNum = startLine + i
            sb.appendLine("$lineNum\t${selectedLines[i]}")

            if (sb.length > maxChars) {
                sb.appendLine()
                sb.appendLine(str(truncateKey).format(sb.length))
                break
            }
        }

        return FileReadResult(sb.toString().trimEnd(), sha256, normalizedPath, startLine, endLine)
    }

    /** readFileContent 的返回类型 */
    private data class FileReadResult(
        val content: String,
        val sha256: String,
        val normalizedPath: java.nio.file.Path,
        val startLine: Int,
        val endLine: Int,
    )
}
