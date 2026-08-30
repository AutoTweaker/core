/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.api.types.tool

import io.github.autotweaker.api.types.serializer.PathSerializer
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * 用于适配器渲染的格式化消息，由工具生成，适配器将不用耦合特定工具的数据。
 *
 * 假设用户在工具的审批、执行、结果展示时都只能看到这些 UiBlock，不包括工具响应、工具请求甚至工具名称。
 */
@Serializable
sealed interface UiBlock {
	/**
	 * 应为单行文本，便于显示。
	 */
	@Serializable
	data class Text(val content: String) : UiBlock
	
	/**
	 * shell 命令。
	 */
	@Serializable
	data class Command(val command: String) : UiBlock
	
	/**
	 * 文件变更的新旧内容，应完整，便于适配器显示正确的行号。
	 *
	 * 对于创建的全新文件，[oldContent] 将为 null。
	 */
	@Serializable
	data class Diff(
		@Serializable(with = PathSerializer::class)
		val filePath: Path,
		val oldContent: String?,
		val newContent: String,
	) : UiBlock
	
	/**
	 * 普通输出。
	 */
	@Serializable
	data class Output(val content: String) : UiBlock
	
	/**
	 * 错误输出。
	 */
	@Serializable
	data class Error(val content: String) : UiBlock
}

/**
 * 应至少包含一个 [UiBlock.Text]。
 */
typealias ToolPresentation = List<UiBlock>

inline fun buildPresentation(block: MutableList<UiBlock>.() -> Unit) =
	mutableListOf<UiBlock>().apply(block).toList()

fun MutableList<UiBlock>.text(content: String) = add(UiBlock.Text(content))
fun MutableList<UiBlock>.command(command: String) = add(UiBlock.Command(command))
fun MutableList<UiBlock>.diff(
	filePath: Path,
	oldContent: String?,
	newContent: String,
) = add(UiBlock.Diff(filePath, oldContent, newContent))

fun MutableList<UiBlock>.output(content: String) = add(UiBlock.Output(content))
fun MutableList<UiBlock>.error(content: String) = add(UiBlock.Error(content))
