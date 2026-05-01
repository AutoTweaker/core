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
		SettingKey("core.agent.tool.response.rejected"),
		SettingItem.Value.ValString("工具调用已被用户拒绝"),
		"工具调用被拒绝时的ToolResult"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.rejected.with.feedback"),
		SettingItem.Value.ValString("用户拒绝了工具调用，并留言：%s"),
		"工具调用被拒绝时的ToolResult"
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
	//工具调用超时
	SettingItem(
		SettingKey("core.agent.tool.timeout.seconds"),
		SettingItem.Value.ValInt(600),
		"工具调用超时时间，单位秒"
	),
	SettingItem(
		SettingKey("core.agent.tool.response.timeout"),
		SettingItem.Value.ValString("工具调用超时（%s秒）"),
		"工具调用超时后的ToolResult"
	),
	//工具激活相关的属性和描述
	SettingItem(
		SettingKey("core.agent.tool.description.enable"),
		SettingItem.Value.ValString("激活此工具以开始使用，无论将此值设为true或false都将启用工具"),
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
	//bash工具相关配置
	SettingItem(
		SettingKey("core.tool.bash.description"),
		SettingItem.Value.ValString("运行一条bash命令，可选设置超时时间并按id注入一次性环境变量"),
		"bash工具的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.function.description.run"),
		SettingItem.Value.ValString("运行一条bash命令并获取输出"),
		"bash_run工具的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.property.description.command"),
		SettingItem.Value.ValString("要执行的bash命令内容"),
		"bash_run工具command参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.property.description.timeout.seconds"),
		SettingItem.Value.ValString("命令超时时间（秒），必须大于0，默认%s秒"),
		"bash_run工具timeout_seconds参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.property.description.env.ids"),
		SettingItem.Value.ValString(
			"要注入的环境变量列表，对于敏感信息，" +
					"严禁令环境变量以任何形式打印到输出或写入文件中。可用列表：%s"
		),
		"bash_run工具env_ids参数的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.message.error.invalid.timeout"),
		SettingItem.Value.ValString("timeout_seconds必须大于0"),
		"bash_run工具timeout_seconds非法时的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.message.error.invalid.command"),
		SettingItem.Value.ValString("command参数不能为空"),
		"bash_run工具command非法时的描述"
	),
	SettingItem(
		SettingKey("core.tool.bash.message.result.template"),
		SettingItem.Value.ValString(
			"""
			命令已执行，退出码：%s，执行时间：%s秒
			标准输出：
			<stdout>
			%s
			</stdout>
			标准错误：
			<stderr>
			%s
			</stderr>
			""".trimIndent()
		),
		"bash_run工具执行结果模板，参数依次为退出码、执行时间（秒）、标准输出、标准错误"
	),
	SettingItem(
		SettingKey("core.tool.bash.setting.default.timeout.seconds"),
		SettingItem.Value.ValInt(60),
		"bash_run工具默认超时时间（秒）"
	),
	//agent模块compact相关配置
	SettingItem(
		SettingKey("core.agent.compact.prompt"),
		SettingItem.Value.ValString(
			"""
			你的任务是对迄今为止的对话进行详细总结，并特别留意用户的明确要求和你之前所执行的操作。
			此摘要应详尽记录技术细节、代码模式以及架构决策，这对于在丢失上下文的情况下继续开发工作至关重要。
		
			在给出最终摘要之前，请将你的分析过程置于 <analysis> 标签内，以整理你的思路，并确保涵盖所有必要要点。在分析过程中：
		
			1. 按时间顺序分析对话中的每条消息及每个部分。对于每个部分，请详尽识别：
			   - 用户的明确请求和意图
			   - 你处理用户请求的方法
			   - 关键决策、技术概念和代码模式
			   - 具体细节，例如：
			     - 文件名称
			     - 完整代码片段
			     - 函数签名
			     - 文件编辑
			   - 你所遇到的错误以及如何修复它们
			   - 尤其要留意用户给出的具体反馈，特别是当用户告诉你采取不同做法的时候。
			2. 仔细检查技术准确性和完整性，确保涵盖每个必需元素。
		
			你的摘要应包含以下部分：
		
			1. 主要请求与意图：详细描述用户的所有明确请求和意图
			2. 关键技术概念：列出所有讨论过的重要技术概念、技术和框架。
			3. 文件与代码片段：枚举所查看、修改或创建的特定文件及代码部分。特别关注最近的消息，并在适用时包含完整代码片段，同时概述该文件读取或编辑为何重要。
			4. 错误与修复：列出你遇到的所有错误及其修复方式。尤其要留意用户的具体反馈，特别是当用户告知你应当采用不同方式时。
			5. 问题解决：描述已解决的问题以及任何正在进行的故障排除工作。
			6. 所有用户消息：列出所有非工具执行结果的用户消息。这些消息对于理解用户的反馈和意图变化至关重要。
			7. 待处理任务：列出你被明确要求处理的任何待处理任务。
			8. 当前工作：详细描述在此次摘要请求之前正在进行的准确工作，尤其关注来自用户和助手的最近消息。在适用时包含文件名称和代码片段。
			9. 可选下一步：列出与你最近进行的工作相关的下一步措施。重要提示：请确保此步骤与你最近的明确用户请求以及摘要请求之前你正在进行的任务直接相关。如果你的上一个任务已经结束，那么仅在这些步骤与你最近的明确用户请求直接相关时才列出后续步骤。在未与用户确认之前，请勿开始处理旁枝末节或早已完成的陈旧请求。
			             如果存在下一步，请包含最近对话的直接引述，以准确展示你正在进行什么任务以及你在何处暂停。此举应一字不差地进行，以确保任务解释不会发生偏移。
		
			以下是你输出内容应遵循的结构示例：
		
			<example>
			<analysis>
			[你的思考过程，确保全面且准确地涵盖所有要点]
			</analysis>
		
			<summary>
			1. 主要请求与意图：
			   [详细描述]
		
			2. 关键技术概念：
			   - [概念 1]
			   - [概念 2]
			   - [...]
		
			3. 文件与代码片段：
			   - [文件名称 1]
			      - [概述为什么此文件很重要]
			      - [概述对此文件所做的更改（若有）]
			      - [重要代码片段]
			   - [文件名称 2]
			      - [重要代码片段]
			   - [...]
		
			4. 错误与修复：
			    - [错误 1 的详细描述]：
			      - [你是如何修复该错误的]
			      - [用户关于该错误的反馈（若有）]
			    - [...]
		
			5. 问题解决：
			   [已解决问题和正在进行的故障排除工作的描述]
		
			6. 所有用户消息：
			    - [详细描述的非工具使用用户消息]
			    - [...]
		
			7. 待处理任务：
			   - [任务 1]
			   - [任务 2]
			   - [...]
		
			8. 当前工作：
			   [当前工作的准确描述]
		
			9. 可选下一步：
			   [可选的下一步行动]
		
			</summary>
			</example>
		
			请根据迄今为止的对话提供你的摘要，遵循此结构，并确保你的回复精确且详尽。
			""".trimIndent()
		),
		"用于上下文压缩的提示词"
	),
	SettingItem(
		SettingKey("core.agent.compact.max.message.chars"),
		SettingItem.Value.ValInt(10000),
		"上下文压缩前对字符数大于此值的消息进行单独总结"
	),
	SettingItem(
		SettingKey("core.agent.compact.message.summarize.prompt"),
		SettingItem.Value.ValString(
			"请对以下消息内容进行概括，输出不要太长\n\n" +
					"<message>\n%s\n</message>"
		),
		"上下文压缩前对字符数过多的消息进行单独总结时的提示词"
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
