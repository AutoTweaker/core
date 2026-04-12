package io.github.whiteelephant.autotweaker.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val toolsConfig: ToolsConfig = ToolsConfig(),
) {
    @Serializable
    data class ToolsConfig(
        val tools: Tools = Tools(),
        val errorJsonParseFailed: String = "错误：参数解析失败，期望 JSON 对象，收到：%s",
        val errorMissingParameter: String = "错误：缺少必需参数 '%s'",
        val errorInvalidParameter: String = "错误：参数 '%s' 无效，期望 %s",
    ) {
        @Serializable
        data class Tools(
            val read: Read = Read(),
        ) {
            @Serializable
            data class Read(
                val maxReadSize: Int = 32000,
                val maxReadLines: Int = 500,

                // tool 元信息
                val toolDescription: String = "按行号范围读取文件内容（使用绝对路径）。一次最多读取 %d 行，总字符数不超过 %d。起始行号和结束行号均从1开始，包含两端。",
                val filePathDescription: String = "要读取的文件的绝对路径",
                val startLineDescription: String = "起始行号（从1开始）",
                val endLineDescription: String = "结束行号（从1开始，包含该行）",

                // 错误消息模板
                val errorNoPendingCall: String = "错误：未找到 '%s' 的待执行调用",
                val errorStartLineTooSmall: String = "错误：start_line 必须大于等于 1",
                val errorEndLineBeforeStart: String = "错误：end_line 必须大于等于 start_line",
                val errorTooManyLines: String = "错误：一次最多读取 %d 行（当前请求 %d 行）",
                val errorNotAbsolute: String = "错误：文件路径必须为绝对路径，收到的是 '%s'",
                val errorFileNotFound: String = "错误：文件不存在 '%s'",
                val errorNotRegularFile: String = "错误：'%s' 不是一个普通文件",
                val errorReadFailed: String = "错误：无法读取文件 '%s': %s",
                val errorStartLineExceeds: String = "错误：起始行号 %d 超出文件总行数 %d",
                val errorTruncated: String = "... [已截断：达到最大字符数限制 %d]",
                val infoDuplicateRead: String = "文件 '%s' 第 %d-%d 行的内容与之前读取的结果相同。",
            )
        }
    }
}
