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

package io.github.autotweaker.api.types.agent

import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.toContentPart
import kotlinx.serialization.Serializable

/**
 * 表示一条用户消息的内容，包含真正的用户消息，或程序注入的指令。
 *
 * 所有字段都可空，一条完全为空或两个字段都 [isEmpty] 的 [MessageContent] 会在消费时被丢弃。
 */
@Serializable
data class MessageContent(
	/**
	 * 程序注入的指令，序列化时包装为 XML 标签。
	 */
	val injections: List<ContextInjection>? = null,
	/**
	 * 用户 prompt，请不要使用此字段发送系统指令。
	 */
	val content: List<ContentPart>? = null,
)

fun MessageContent(content: String) = MessageContent(
	injections = null,
	content = content.toContentPart()
)
