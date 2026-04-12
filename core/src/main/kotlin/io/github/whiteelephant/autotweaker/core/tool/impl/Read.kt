package io.github.whiteelephant.autotweaker.core.tool.impl

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import io.github.whiteelephant.autotweaker.core.data.DataModule
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.tool.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class Read : Tool {
    private val config by DataModule

    private val readConfig get() = config.toolsConfig.tools.read

    override val name: String = "read_file"

    override val description: String
        get() = String.format(readConfig.toolDescription, readConfig.maxReadLines, readConfig.maxReadSize)

    override val functions: List<ChatRequest.Tool.Parameters>
        get() = listOf(
            ChatRequest.Tool.Parameters(
                properties = mapOf(
                    "file_path" to ChatRequest.Tool.Parameters.Property(
                        type = ChatRequest.Tool.Parameters.Property.Type.STRING,
                        description = readConfig.filePathDescription,
                    ),
                    "start_line" to ChatRequest.Tool.Parameters.Property(
                        type = ChatRequest.Tool.Parameters.Property.Type.INTEGER,
                        description = readConfig.startLineDescription,
                    ),
                    "end_line" to ChatRequest.Tool.Parameters.Property(
                        type = ChatRequest.Tool.Parameters.Property.Type.INTEGER,
                        description = readConfig.endLineDescription,
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
            ?: throw IllegalStateException("No pending tool call found for '$name'")

        val toolsConfig = config.toolsConfig

        val args = try {
            Json.parseToJsonElement(pendingCall.arguments).jsonObject
        } catch (e: Exception) {
            return String.format(toolsConfig.errorJsonParseFailed, pendingCall.arguments)
        }

        val filePath = args["file_path"]?.jsonPrimitive?.content
            ?: return String.format(toolsConfig.errorMissingParameter, "file_path")
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull
            ?: return String.format(toolsConfig.errorInvalidParameter, "start_line", "integer")
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull
            ?: return String.format(toolsConfig.errorInvalidParameter, "end_line", "integer")

        if (startLine < 1) {
            return readConfig.errorStartLineTooSmall
        }
        if (endLine < startLine) {
            return readConfig.errorEndLineBeforeStart
        }
        val requestedLines = endLine - startLine + 1
        if (requestedLines > readConfig.maxReadLines) {
            return String.format(readConfig.errorTooManyLines, readConfig.maxReadLines, requestedLines)
        }

        val path = Path(filePath)

        if (!path.isAbsolute) {
            return String.format(readConfig.errorNotAbsolute, filePath)
        }

        val normalizedPath = path.normalize()

        if (!normalizedPath.exists()) {
            return String.format(readConfig.errorFileNotFound, filePath)
        }

        if (!normalizedPath.isRegularFile()) {
            return String.format(readConfig.errorNotRegularFile, filePath)
        }

        val allLines: List<String> = try {
            Files.readAllLines(normalizedPath)
        } catch (e: Exception) {
            return String.format(readConfig.errorReadFailed, filePath, e.message)
        }

        val totalLines = allLines.size

        if (startLine > totalLines) {
            return String.format(readConfig.errorStartLineExceeds, startLine, totalLines)
        }

        val actualEndLine = minOf(endLine, totalLines)
        val selectedLines = allLines.subList(startLine - 1, actualEndLine)

        val sb = StringBuilder()
        for (i in selectedLines.indices) {
            val lineNum = startLine + i
            sb.appendLine("$lineNum\t${selectedLines[i]}")

            if (sb.length > readConfig.maxReadSize) {
                sb.appendLine()
                sb.appendLine(String.format(readConfig.errorTruncated, readConfig.maxReadSize))
                break
            }
        }

        val content = sb.toString().trimEnd()

        if (isDuplicateRead(context, filePath, startLine, actualEndLine, content)) {
            return String.format(readConfig.infoDuplicateRead, filePath, startLine, actualEndLine)
        }

        return content
    }

    private fun isDuplicateRead(
        context: AgentContext,
        filePath: String,
        startLine: Int,
        endLine: Int,
        currentContent: String,
    ): Boolean {
        val currentKey = "$filePath:$startLine-$endLine"

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
