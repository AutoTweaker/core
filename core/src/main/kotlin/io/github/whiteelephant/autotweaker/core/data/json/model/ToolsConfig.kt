package io.github.whiteelephant.autotweaker.core.data.json.model

import kotlinx.serialization.Serializable

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
            val summarizeModel: String? = null,
            val summarizeMaxSize: Int = 100000,
            val summarizeSystemPrompt: String = "你是一个文件总结助手，请遵循以下指令对用户提供的代码内容进行简洁的总结。",

            val toolDescription: String = "按行号范围读取文件内容（使用绝对路径）。一次最多读取 %d 行，总字符数不超过 %d。起始行号和结束行号均从1开始，包含两端。支持 summarize 参数对文件内容进行总结。",
            val filePathDescription: String = "要读取的文件的绝对路径",
            val startLineDescription: String = "起始行号（从1开始）",
            val endLineDescription: String = "结束行号（从1开始，包含该行）",
            val summarizeDescription: String = "对读取的内容进行总结时的要求，留空或不返回此字段将不进行总结",

            val readFileDescription: String = "读取指定行号范围的文件内容",
            val summarizeFileDescription: String = "读取文件内容并使用 LLM 进行总结",
            val unicodeCodesDescription: String = "读取文件内容并返回所有字符的 Unicode 代码",

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
            val errorSummarizeFailed: String = "总结失败：%s",
            val errorSummarizeEmpty: String = "总结为空",
            val infoFileContentEmpty: String = "文件 '%s' 内容为空（仅包含换行符和空格）",//TODO 为空但包含换行符或空格时返回Unicode代码
        )
    }
}
