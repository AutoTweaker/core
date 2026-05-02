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

package io.github.autotweaker.core.llm.provider.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = DeepSeekMessageSerializer::class)
sealed class DeepSeekMessage {
	abstract val role: String
	abstract val content: String?
	
	@Serializable
	data class SystemMessage(
		override val role: String = "system",
		override val content: String,
		val name: String? = null
	) : DeepSeekMessage()
	
	@Serializable
	data class UserMessage(
		override val role: String = "user",
		override val content: String,
		val name: String? = null
	) : DeepSeekMessage()
	
	@Serializable
	data class AssistantMessage(
		override val role: String = "assistant",
		override val content: String?,
		@SerialName("reasoning_content")
		val reasoningContent: String? = null,
		val name: String? = null,
		@SerialName("tool_calls")
		val toolCalls: List<ToolCall>? = null
	) : DeepSeekMessage() {
		@Serializable
		data class ToolCall(
			val id: String,
			val type: String = "function",
			val function: Function
		) {
			@Serializable
			data class Function(
				val name: String,
				val arguments: String
			)
		}
	}
	
	@Serializable
	data class ToolMessage(
		override val role: String = "tool",
		override val content: String,
		@SerialName("tool_call_id")
		val toolCallId: String
	) : DeepSeekMessage()
}

object DeepSeekMessageSerializer : JsonContentPolymorphicSerializer<DeepSeekMessage>(DeepSeekMessage::class) {
	override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement) = when {
		"tool_call_id" in element.jsonObject -> DeepSeekMessage.ToolMessage.serializer()
		element.jsonObject["role"]?.jsonPrimitive?.content == "assistant" -> DeepSeekMessage.AssistantMessage.serializer()
		element.jsonObject["role"]?.jsonPrimitive?.content == "system" -> DeepSeekMessage.SystemMessage.serializer()
		else -> DeepSeekMessage.UserMessage.serializer()
	}
}
