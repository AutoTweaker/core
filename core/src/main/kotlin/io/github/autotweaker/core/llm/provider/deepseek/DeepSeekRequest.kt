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

import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class DeepSeekRequest(
	val messages: List<DeepSeekMessage>,
	@SerialName("stream_options")
	val streamOptions: StreamOptions? = null,
	override val tools: List<Tool>? = null,
	@SerialName("tool_choice")
	val toolChoice: ToolChoice? = null,
	override val model: String,
	override val thinking: Thinking? = null,
	@SerialName("max_tokens")
	override val maxCompletionTokens: Int? = null,
	@SerialName("frequency_penalty")
	override val frequencyPenalty: Double? = null,
	@SerialName("presence_penalty")
	override val presencePenalty: Double? = null,
	@SerialName("response_format")
	override val responseFormat: ChatRequest.ResponseFormat? = null,
	override val stop: List<String>? = null,
	override val stream: Boolean? = null,
	override val temperature: Double? = null,
	@SerialName("top_p")
	override val topP: Double? = null

) : OpenAiRequest() {
	@Serializable
	data class StreamOptions(
		@SerialName("include_usage")
		val includeUsage: Boolean?
	)
}


@Serializable(with = ToolChoice.Serializer::class)
sealed class ToolChoice {
	data class Simple(
		val mode: Mode
	) : ToolChoice() {
		@Serializable
		enum class Mode {
			@SerialName("none")
			NONE,
			
			@SerialName("auto")
			AUTO,
			
			@SerialName("required")
			REQUIRED
		}
	}
	
	@Serializable
	data class Specific(
		val type: String = "function",
		val function: NamedFunction
	) : ToolChoice() {
		@Serializable
		data class NamedFunction(
			val name: String
		)
	}
	
	@Suppress("unused")
	companion object {
		val NONE = Simple(Simple.Mode.NONE)
		val AUTO = Simple(Simple.Mode.AUTO)
		val REQUIRED = Simple(Simple.Mode.REQUIRED)
		
		fun function(name: String) = Specific(function = Specific.NamedFunction(name))
	}
	
	object Serializer : KSerializer<ToolChoice> {
		override val descriptor: SerialDescriptor =
			buildClassSerialDescriptor("ToolChoice")
		
		override fun serialize(encoder: Encoder, value: ToolChoice) {
			val jsonEncoder = encoder as? JsonEncoder
				?: throw SerializationException("Only JSON is supported")
			
			when (value) {
				is Simple -> {
					// 直接序列化内部枚举，输出结果为字符串，例如 "auto"
					jsonEncoder.encodeJsonElement(
						jsonEncoder.json.encodeToJsonElement(Simple.Mode.serializer(), value.mode)
					)
				}
				
				is Specific -> {
					// 序列化整个对象结构
					jsonEncoder.encodeJsonElement(
						jsonEncoder.json.encodeToJsonElement(Specific.serializer(), value)
					)
				}
			}
		}
		
		override fun deserialize(decoder: Decoder): ToolChoice {
			val jsonDecoder = decoder as? JsonDecoder
				?: throw SerializationException("Only JSON is supported")
			val element = jsonDecoder.decodeJsonElement()
			
			return if (element is JsonPrimitive) {
				// 如果是字符串，解析为枚举并包装在 Simple 中
				val mode = jsonDecoder.json.decodeFromJsonElement(Simple.Mode.serializer(), element)
				Simple(mode)
			} else {
				// 如果是对象，解析为 Specific
				jsonDecoder.json.decodeFromJsonElement(Specific.serializer(), element)
			}
		}
	}
}
