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

import io.github.autotweaker.api.types.Sha256
import kotlinx.serialization.Serializable

/**
 * 表示一条用户消息的内容，包含真正的用户消息，或程序注入的指令。
 *
 * 所有字段都可空，一条完全为空或三个字段都 [isEmpty] 的 [MessageContent] 会在消费时被丢弃。
 * 也包括空的 [content]，对应 [String.isEmpty]，但 [String.isBlank] 不算作“空”。
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
	val content: String? = null,
	/**
	 * 用户发送的图片，请使用 [io.github.autotweaker.api.ObjectStorable] 获取 [io.github.autotweaker.api.storage.ObjectStorage]，并调用 [io.github.autotweaker.api.storage.ObjectStorage.put] 来存储用户提供的图片。
	 *
	 * 无效的 [Sha256] 无法保证行为，取决于 [io.github.autotweaker.api.llm.LlmClient]，请确保 [Sha256] 有效。
	 *
	 * [io.github.autotweaker.api.storage.ObjectStorage] 不具备自动清理机制，只要 [Sha256] 来自 [io.github.autotweaker.api.storage.ObjectStorage.put] 就不会出现问题。
	 */
	val images: List<Sha256>? = null,
)
