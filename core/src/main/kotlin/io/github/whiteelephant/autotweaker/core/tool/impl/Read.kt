package io.github.whiteelephant.autotweaker.core.tool.impl

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.tool.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class Read : Tool {

    companion object {
        const val MAX_LINES = 500
        const val MAX_CHARACTERS = 50_000
    }

    override val name: String = "read_file"

    override val description: String =
        "按行号范围读取文件内容（使用绝对路径）。一次最多读取 $MAX_LINES 行，总字符数不超过 $MAX_CHARACTERS。" +
        "起始行号和结束行号均从1开始，包含两端。"

    override val functions: List<ChatRequest.Tool.Parameters> = listOf(
        ChatRequest.Tool.Parameters(
            properties = mapOf(
                "file_path" to ChatRequest.Tool.Parameters.Property(
                    type = ChatRequest.Tool.Parameters.Property.Type.STRING,
                    description = "要读取的文件的绝对路径",
                ),
                "start_line" to ChatRequest.Tool.Parameters.Property(
                    type = ChatRequest.Tool.Parameters.Property.Type.INTEGER,
                    description = "起始行号（从1开始）",
                ),
                "end_line" to ChatRequest.Tool.Parameters.Property(
                    type = ChatRequest.Tool.Parameters.Property.Type.INTEGER,
                    description = "结束行号（从1开始，包含该行）",
                ),
            ),
            required = listOf("file_path", "start_line", "end_line"),
        )
    )

    override suspend fun execute(
        context: AgentContext,
    ): String {
        val pendingCall = context.currentRound?.pendingToolCalls
            ?.firstOrNull { it.name == name }
            ?: return "错误：未找到 '$name' 的待执行调用"

        val args = try {
            Json.parseToJsonElement(pendingCall.arguments).jsonObject
        } catch (e: Exception) {
            return "错误：参数解析失败，期望 JSON 对象，收到：${pendingCall.arguments}"
        }

        val filePath = args["file_path"]?.jsonPrimitive?.content
            ?: return "错误：缺少必需参数 'file_path'"
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull
            ?: return "错误：缺少或无效的必需参数 'start_line'（期望整数）"
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull
            ?: return "错误：缺少或无效的必需参数 'end_line'（期望整数）"

        if (startLine < 1) {
            return "错误：start_line 必须大于等于 1"
        }
        if (endLine < startLine) {
            return "错误：end_line 必须大于等于 start_line"
        }
        val requestedLines = endLine - startLine + 1
        if (requestedLines > MAX_LINES) {
            return "错误：一次最多读取 $MAX_LINES 行（当前请求 $requestedLines 行）"
        }

        val path = Path(filePath)

        if (!path.isAbsolute) {
            return "错误：文件路径必须为绝对路径，收到的是 '$filePath'"
        }

        val normalizedPath = path.normalize()

        if (!normalizedPath.exists()) {
            return "错误：文件不存在 '$filePath'"
        }

        if (!normalizedPath.isRegularFile()) {
            return "错误：'$filePath' 不是一个普通文件"
        }

        val allLines: List<String> = try {
            Files.readAllLines(normalizedPath)
        } catch (e: Exception) {
            return "错误：无法读取文件 '$filePath': ${e.message}"
        }

        val totalLines = allLines.size

        if (startLine > totalLines) {
            return "错误：起始行号 $startLine 超出文件总行数 $totalLines"
        }

        val actualEndLine = minOf(endLine, totalLines)
        val selectedLines = allLines.subList(startLine - 1, actualEndLine)

        val sb = StringBuilder()
        for (i in selectedLines.indices) {
            val lineNum = startLine + i
            sb.appendLine("$lineNum\t${selectedLines[i]}")

            if (sb.length > MAX_CHARACTERS) {
                sb.appendLine()
                sb.appendLine("... [已截断：达到最大字符数限制 $MAX_CHARACTERS]")
                break
            }
        }

        val content = sb.toString().trimEnd()

        // 检查历史中是否有相同文件路径+行号范围+内容的读取
        if (isDuplicateRead(context, filePath, startLine, actualEndLine, content)) {
            return "文件 '$filePath' 第 ${startLine}-${actualEndLine} 行的内容与之前读取的结果相同。"
        }

        return content
    }

    /**
     * 检查 AgentContext 历史中是否已有相同文件路径、行号范围且内容相同的读取结果。
     * 仅检查 historyRounds 和 currentRound，不检查 compactedRounds。
     */
    private fun isDuplicateRead(
        context: AgentContext,
        filePath: String,
        startLine: Int,
        endLine: Int,
        currentContent: String,
    ): Boolean {
        val currentKey = "$filePath:$startLine-$endLine"

        // 收集历史中所有 read_file 的 tool 调用
        val historyTools = mutableListOf<AgentContext.Message.Tool>()

        context.historyRounds?.forEach { round ->
            round.turns?.forEach { turn ->
                turn.tools.forEach { tool ->
                    if (tool.name == name) {
                        historyTools.add(tool)
                    }
                }
            }
        }

        context.currentRound?.turns?.forEach { turn ->
            turn.tools.forEach { tool ->
                if (tool.name == name) {
                    historyTools.add(tool)
                    }
            }
        }

        for (tool in historyTools) {
            val prevArgs = try {
                Json.parseToJsonElement(tool.call.arguments).jsonObject
            } catch (e: Exception) {
                continue
            }
            val prevFilePath = prevArgs["file_path"]?.jsonPrimitive?.content ?: continue
            val prevStartLine = prevArgs["start_line"]?.jsonPrimitive?.intOrNull ?: continue
            val prevEndLine = prevArgs["end_line"]?.jsonPrimitive?.intOrNull ?: continue

            if ("$prevFilePath:$prevStartLine-$prevEndLine" == currentKey
                && tool.result.content == currentContent
            ) {
                return true
            }
        }

        return false
    }
}
