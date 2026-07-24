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

import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.serializer.InstantLongSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.tool.ToolResultStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

/**
 * 表示 Agent 会话中的一条消息。
 */
@Serializable
sealed class AgentMessage {
	/**
	 * 消息 id，每种消息都有。
	 *
	 * 同样的消息 id 可能出现在不同 Agent 或不同会话中，除非是 [UUID.randomUUID] 撞了，否则代表它们同根同源。
	 */
	@Serializable(with = UuidSerializer::class)
	abstract val id: UUID
	
	/**
	 * 消息时间戳，每种消息都有。
	 */
	@Serializable(with = InstantLongSerializer::class)
	abstract val timestamp: Instant
	
	/**
	 * 表示一条用户消息。
	 */
	@Serializable
	data class User(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		/**
		 * 用户消息的内容，也可能包含系统注入。
		 *
		 * @see MessageContent
		 */
		val content: MessageContent,
	) : AgentMessage()
	
	/**
	 * 表示一条 LLM 返回的消息。
	 */
	@Serializable
	data class Assistant(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		/**
		 * LLM 思维链。
		 */
		val reasoning: String?,
		/**
		 * LLM 响应正文。
		 */
		val content: String?,
		/**
		 * 请求使用的模型 id。
		 */
		@Serializable(with = UuidSerializer::class)
		val model: UUID,
		/**
		 * 用量信息，包含模型定价快照以防止价格变更导致计费不准确。
		 *
		 * @see UsageSnapshot
		 */
		val usageSnapshot: UsageSnapshot? = null,
	) : AgentMessage()
	
	/**
	 * 表示一次工具调用。
	 */
	@Serializable
	sealed class Tool : AgentMessage() {
		/**
		 * 工具调用的 id，来自大模型 api，用于帮助模型匹配 [Call] 和 [Result]。
		 */
		abstract val callId: String
		
		/**
		 * 工具调用请求，来自 LLM。
		 */
		@Serializable
		data class Call(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			@Serializable(with = InstantLongSerializer::class)
			override val timestamp: Instant,
			override val callId: String,
			/**
			 * 工具调用的请求名称，可能同时包含工具名称和 function 名称。
			 */
			val callName: String,
			/**
			 * 工具调用的请求参数，为来自 api 响应的原始参数，可能不是一个有效的 Json 对象。
			 */
			val arguments: String,
			/**
			 * 工具调用的原因，只有解析成功的工具调用会包含此字段。
			 */
			val reason: String?,
			/**
			 * 业务层的工具名称，而非 [callName] 那样包含 function 名。
			 *
			 * 对应 [io.github.autotweaker.api.types.tool.ToolMeta.name]。
			 */
			val validatedToolName: String?,
			/**
			 * 成功反序列化为工具的 [io.github.autotweaker.api.tool.ToolArgs] 后序列化到 [JsonElement]。
			 *
			 * 使用 [io.github.autotweaker.api.adapter.CoreAPI.ToolAPI.deserializeArgs] 可拿到 [io.github.autotweaker.api.tool.ToolArgs] 对象。
			 */
			val validatedArgs: JsonElement?,
		) : Tool()
		
		/**
		 * 工具调用响应，来自程序。
		 */
		@Serializable
		data class Result(
			@Serializable(with = UuidSerializer::class)
			override val id: UUID,
			@Serializable(with = InstantLongSerializer::class)
			override val timestamp: Instant,
			override val callId: String,
			/**
			 * 响应内容，不一定是结构化数据。
			 */
			val content: String,
			/**
			 * 工具调用的结果（状态），前端可以据此调整显示。
			 *
			 * @see ToolResultStatus
			 */
			val status: ToolResultStatus
		) : Tool()
	}
	
	/**
	 * 上下文压缩的响应消息。
	 */
	@Serializable
	data class Compact(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		/**
		 * 上下文压缩的结果，不同于 [AgentOutput.Compact]，这里没有 XML 标签，是纯净的总结内容。
		 */
		val content: String,
		/**
		 * 用量信息，由于上下文压缩会由于响应无效而自动重试，可能产生多个 [io.github.autotweaker.api.types.llm.Usage]。
		 *
		 * 不同于普通的 LLM 消息，就算存在请求重试机制，失败的请求也根本不会拿到 [io.github.autotweaker.api.types.llm.Usage] 响应，它仅存在于已完成且技术上成功的消息，而这种消息会被算作一条单独的 [AgentMessage.Assistant]。
		 */
		val snapshots: Map<@Serializable(with = UuidSerializer::class) UUID, UsageSnapshot>? = null,
	) : AgentMessage()
	
	/**
	 * 用于跟踪不属于任何 [AgentMessage] 的 LLM 开销，例如 Agent 调用的工具调用了 LLM，或彻底失败的上下文压缩（不会产生 [AgentMessage.Compact] 来跟踪开销）。
	 *
	 * 这并非能够索引所有类型的未跟踪开销，例如 [io.github.autotweaker.api.adapter.CoreAPI.chat] 就会直接存储到全局的上下文跟踪器。
	 */
	@Serializable
	data class UsageRecord(
		@Serializable(with = UuidSerializer::class)
		override val id: UUID,
		@Serializable(with = InstantLongSerializer::class)
		override val timestamp: Instant,
		/**
		 * @see Assistant.usageSnapshot
		 */
		val snapshot: UsageSnapshot,
	) : AgentMessage()
}
