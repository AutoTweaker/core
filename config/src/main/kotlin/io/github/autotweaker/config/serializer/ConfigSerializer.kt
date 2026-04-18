package io.github.autotweaker.config.serializer

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

val defaultItems: List<SettingItem> = listOf(
    //agent模块的tool相关配置
    SettingItem(
        SettingKey("core.agent.tool.response.canceled"),
        SettingItem.Value.ValString("工具调用已取消"),
        "工具调用被取消时的ToolResult"
    ),
    //read工具相关配置
    SettingItem(
        SettingKey("core.tool.read.description"),
        SettingItem.Value.ValString("读取一个文件，支持获取摘要以及Unicode代码"),
        "read工具的描述，在read工具未激活时展示给llm"
    ),
    SettingItem(
        SettingKey("core.tool.read.function.file.description"),
        SettingItem.Value.ValString("读取一个文件，字符数和总行数受到限制"),
        "read_file工具的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.function.summarize.description"),
        SettingItem.Value.ValString("获取一个文件的摘要，字符数和总行数的限制更宽松，对最小字符数有额外限制，在文件较大时使用此工具很合适"),
        "read_summarize工具的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.function.unicode.description"),
        SettingItem.Value.ValString("读取一个文件但是返回每个字符的Unicode编码，在普通读取看起来没有返回有效内容时使用"),
        "read_unicode工具的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.property.file.path.description"),
        SettingItem.Value.ValString("文件的路径，必须为**绝对路径**"),
        "read工具file_path参数的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.property.start.line.description"),
        SettingItem.Value.ValString("读取文件的开始行号，从1开始"),
        "read工具start_line参数的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.property.end.line.description"),
        SettingItem.Value.ValString("读取文件的结束行号，不能小于开始行号，可以大于文件总行数"),
        "read工具end_line参数的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.function.summarize.property.prompt.description"),
        SettingItem.Value.ValString("用于总结文件的提示词，调整此字段来要求总结器关注不同细节"),
        "read_summarize工具prompt参数的描述"
    ),
    SettingItem(
        SettingKey("core.tool.read.function.unicode.property.max.chars.description"),
        SettingItem.Value.ValString("读取文件的前多少个字符，这将包括换行符等特殊字符"),
        "read_unicode工具max_chars参数的描述"
    ),
    //TODO 错误消息、数值参数
)

private val json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

fun serializeToFile(items: List<SettingItem>, outputPath: String) {
    val content = json.encodeToString(ListSerializer(SettingItem.serializer()), items)
    val outputFile = File(outputPath)
    outputFile.parentFile.mkdirs()
    outputFile.writeText(content)
    println("Config serialized to ${outputFile.absolutePath}")
    println("Total items: ${items.size}")
}

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: throw IllegalArgumentException("Output path is required")
    serializeToFile(defaultItems, outputPath)
}
