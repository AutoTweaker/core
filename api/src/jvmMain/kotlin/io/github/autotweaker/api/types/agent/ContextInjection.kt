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

import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

/**
 * 表示一条程序注入的指令，序列化时包装为 XML 标签。
 *
 * 对于 [MessageContent] 中的临时注入（跟随用户消息在上下文中的生命周期），请使用同名方法省略 [id] 传参或 [tag] / [content] 的实参名称。
 */
@Serializable
data class ContextInjection(
	/**
	 * id 仅用于对于 Agent 会话级的注入，对于 [MessageContent] 这种用户消息，甚至不会用于去重。
	 *
	 * @see io.github.autotweaker.api.adapter.AgentAPI.inject
	 * @see io.github.autotweaker.api.adapter.AgentAPI.removeInjection
	 */
	@Serializable(with = UuidSerializer::class)
	val id: UUID = UUID.randomUUID(),
	/**
	 * XML 标签的名称。
	 */
	val tag: String,
	/**
	 * XML 标签的内容，序列化时不会作任何转义处理，如果内容来自外部输入，请自行进行 XML 转义。
	 */
	val content: String,
)

/**
 * 快速构造 [io.github.autotweaker.api.types.agent.ContextInjection] 而无需将名称添加到调用实参或手动传递 [UUID]。
 *
 * 自动对 [content] 进行 [toString]。
 *
 * @param tag XML 标签名称，参见 [io.github.autotweaker.api.types.agent.ContextInjection]。
 * @param content XML 标签内容，只有在确保对象 [toString] 格式友好的情况下才可直接传递对象，否则应当手动构造 [String]。
 */
fun ContextInjection(
	tag: String,
	content: Any
) = ContextInjection(
	tag = tag,
	content = content.toString()
)
