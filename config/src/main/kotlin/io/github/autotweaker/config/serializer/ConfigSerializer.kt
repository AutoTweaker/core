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
	SettingItem(
		SettingKey("core.agent.tool.response.property.missing"),
		SettingItem.Value.ValString("%s工具需要属性：%s"),
		"工具调用缺少属性时的ToolResult"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.property.error"),
		SettingItem.Value.ValString("%s工具的属性%s必须为%s类型"),
		"工具调用属性格式错误时的ToolResult"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.function.name.error"),
		SettingItem.Value.ValString("%s工具不存在，请检查工具是否已激活"),
		"调用工具不存在时的ToolResult"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.json.error"),
		SettingItem.Value.ValString("调用参数不是一个有效的JSON对象：%s"),
		"工具调用参数无法解析时的ToolResult"
	),
	SettingItem(
		SettingKey("core.agent.tool.description.reason"),
		SettingItem.Value.ValString("简要描述调用此工具的目的"),
		"工具调用的reason属性描述"
	),
	//工具激活相关的属性和描述
	SettingItem(
		SettingKey("core.agent.tool.description.enable"),
		SettingItem.Value.ValString("激活此工具以开始使用"),
		"未激活工具的enable属性描述"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.active"),
		SettingItem.Value.ValString("工具%s已激活，包含%s个function，检查你的工具列表来了解如何使用"),
		"激活工具后的ToolResult"
	),
	//工具模块相关配置
	SettingItem(
		SettingKey("core.tool.message.path.error"),
		SettingItem.Value.ValString("提供的路径不合法，请检查提供的路径参数"),
		"路径解析失败时的描述"
	),
	//read工具相关配置
	SettingItem(
		SettingKey("core.tool.read.summarize.prompt"),
		SettingItem.Value.ValString("你是文件总结助手，请根据用户输入和以下指令生成关于文件内容的摘要"),
		"summarize功能使用的系统提示词，这段文本被安置在llm自定义指令之前"
	),
	//工具描述
	SettingItem(
		SettingKey("core.tool.read.description"),
		SettingItem.Value.ValString("读取一个文件，支持获取摘要以及Unicode代码"),
		"read工具的描述，在read工具未激活时展示给llm"
	),
	//function描述
	SettingItem(
		SettingKey("core.tool.read.function.description.file"),
		SettingItem.Value.ValString("读取一个文件，最大字符数%s，最大行数%s，返回内容的第一行为文件内容的SHA256，第二行开始是文件内容，注意区分"),
		"read_file工具的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.description.summarize"),
		SettingItem.Value.ValString("获取一个文件的摘要，最大字符数%s，最小字符数%s，最大行数%s，在文件较大时使用此工具很合适"),
		"read_summarize工具的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.description.unicode"),
		SettingItem.Value.ValString("读取一个文件但是返回每个字符的Unicode编码，在普通读取看起来没有返回有效内容时使用"),
		"read_unicode工具的描述"
	),
	//属性描述
	SettingItem(
		SettingKey("core.tool.read.property.description.file.path"),
		SettingItem.Value.ValString("文件的路径，支持绝对路径或相对路径"),
		"read工具file_path参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.property.description.start.line"),
		SettingItem.Value.ValString("读取文件的开始行号，从1开始"),
		"read工具start_line参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.property.description.end.line"),
		SettingItem.Value.ValString("读取文件的结束行号，不能小于开始行号，可以大于文件总行数"),
		"read工具end_line参数的描述"
	),
	//特定function的属性描述
	SettingItem(
		SettingKey("core.tool.read.function.description.file.property.line.number"),
		SettingItem.Value.ValString("是否启用行号，默认为true，启用行号后会在每行的开头添加[行号][制表符]作为前缀，注意区分"),
		"read_summarize工具line_number参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.description.summarize.property.prompt"),
		SettingItem.Value.ValString("用于总结文件的提示词，调整此字段来要求总结器关注不同细节"),
		"read_summarize工具prompt参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.description.unicode.property.max.chars"),
		SettingItem.Value.ValString("读取文件的前n个字符，这将包括换行符等特殊字符，最多%s个字符"),
		"read_unicode工具max_chars参数的描述"
	),
	//错误消息
	SettingItem(
		SettingKey("core.tool.read.message.error.too.many.lines"),
		SettingItem.Value.ValString("读取的行数过多，上限为%s"),
		"read工具读取过多内容时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.message.error.file.not.found"),
		SettingItem.Value.ValString("文件不存在或访问被拒绝"),
		"read工具读取不存在的文件时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.message.error.file.can.not.read"),
		SettingItem.Value.ValString("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏"),
		"read工具读取的文件无法解析时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.message.error.start.line"),
		SettingItem.Value.ValString("start_line必须大于或等于1"),
		"read工具start_line不合法的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.message.error.start.line.bigger.than.end.line"),
		SettingItem.Value.ValString("start_line不能大于end_line"),
		"read工具start_line大于end_line的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.error.file.duplicate"),
		SettingItem.Value.ValString("读取的文件内容与文件哈希%s时的读取相同"),
		"read_file工具读取重复内容时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.error.unicode.too.many.chars"),
		SettingItem.Value.ValString("读取的字符数过多，上限为%s"),
		"read_unicode工具读取过多内容时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.error.summarize.too.few"),
		SettingItem.Value.ValString("用于总结的字符数过少（%s），必须大于%s"),
		"read_summarize工具总结内容过少时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.error.summarize.failed"),
		SettingItem.Value.ValString("总结器出错，请及时告知用户：%s"),
		"read_summarize总结llm出错时的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.file.truncate"),
		SettingItem.Value.ValString("<字符数过多，后续内容已被截断（共%s字符），请尝试使用read_summarize工具>"),
		"read_file工具截断位置的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.summarize.output.truncate"),
		SettingItem.Value.ValString("<总结器输出内容过多，后续内容已被截断（共%s字符），请尝试修改总结器提示词>"),
		"read_summarize工具截断位置的描述"
	),
	SettingItem(
		SettingKey("core.tool.read.function.message.summarize.input.truncate"),
		SettingItem.Value.ValString("<字符数过多，后续内容已被截断（共%s字符）>"),
		"read_summarize工具总结器输入内容截断位置的描述"
	),
	//数值参数
	SettingItem(
		SettingKey("core.tool.read.function.file.setting.max.lines"),
		SettingItem.Value.ValInt(500),
		"read_file工具最大允许行数"
	),
	SettingItem(
		SettingKey("core.tool.read.function.file.setting.max.chars"),
		SettingItem.Value.ValInt(20000),
		"read_file工具最大允许字符数，超出会截断"
	),
	SettingItem(
		SettingKey("core.tool.read.function.summarize.setting.max.lines"),
		SettingItem.Value.ValInt(5000),
		"read_summarize工具最大允许行数"
	),
	SettingItem(
		SettingKey("core.tool.read.function.summarize.setting.max.input.chars"),
		SettingItem.Value.ValInt(200000),
		"read_summarize工具最大输入字符数，超出会截断"
	),
	SettingItem(
		SettingKey("core.tool.read.function.summarize.setting.min.chars"),
		SettingItem.Value.ValInt(500),
		"read_summarize工具最小允许字符数，小于此会返回错误消息"
	),
	SettingItem(
		SettingKey("core.tool.read.function.summarize.setting.max.output.chars"),
		SettingItem.Value.ValInt(5000),
		"read_summarize工具最大输出字符数，超出会截断"
	),
	SettingItem(
		SettingKey("core.tool.read.function.unicode.setting.max.chars"),
		SettingItem.Value.ValInt(500),
		"read_unicode工具最大允许字符数，超出会返回错误消息"
	),
)

private val json = Json {
	prettyPrint = false
	ignoreUnknownKeys = true
}

@Suppress("ReplacePrintlnWithLogging")
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
	val duplicates = defaultItems.groupBy { it.key }.filter { it.value.size > 1 }.keys
	require(duplicates.isEmpty()) {
		"Duplicate SettingKey found: ${duplicates.joinToString { it.value }}"
	}
	serializeToFile(defaultItems, outputPath)
}
