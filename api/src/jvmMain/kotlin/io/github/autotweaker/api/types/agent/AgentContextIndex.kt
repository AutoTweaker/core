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

import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

/**
 * 表示 Agent 的上下文，不持有任何消息，而是使用 [UUID] 索引。
 *
 * 在 AgentContext 的模型中，每条用户消息都会开启一个独立的轮次，每个轮次中 Agent 可能进行多轮工具调用，这些中间的工具调用称为 Turn。
 *
 * 每个 Turn 记录一条 Agent 消息和若干工具请求以及响应，Agent 在下一个 Turn 思考并继续调用工具。
 *
 * 如果 Agent 没有调用工具，Turn 将不会被创建，Agent 的消息将作为 finalAssistantMessage，[CurrentRound] 会被归档，转换为一条 [CompletedRound] 并进入 historyRounds。
 *
 * @property compactedRounds 压缩自 [historyRounds] 的消息，只有一条 summary 进入实际上下文。
 * @property historyRounds 已经完成的轮次。
 * @property currentRound 正在进行的轮次。
 */
@Serializable
data class AgentContextIndex(
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<CompletedRound>?,
	val currentRound: CurrentRound?,
) : UuidIndex {
	override fun ids(): Set<UUID> =
		compactedRounds?.ids().orEmpty() +
				historyRounds?.flatMap { it.ids() }.orEmpty() +
				currentRound?.ids().orEmpty()
	
	/**
	 * 上下文压缩产生的归档，包含 summary 和 summary 覆盖的若干 [CompletedRound]。
	 *
	 * 上下文压缩后，[AgentContextIndex.compactedRounds] 会更新，[AgentContextIndex.historyRounds] 中的相关轮次会被移动到此。
	 *
	 * @property compactedRounds 上下文压缩运行时会包含上次的 summary 进行总结，此字段刚好引用了更早的归档。
	 * @property rounds 上下文压缩覆盖的历史轮次，来自 historyRounds。
	 * @property summarizedMessage LLM 生成的总结消息，参见 [AgentMessage.Compact]。
	 */
	@Serializable
	data class CompactedRounds(
		val compactedRounds: CompactedRounds?,
		val rounds: List<CompletedRound>,
		
		@Serializable(with = UuidSerializer::class)
		val summarizedMessage: UUID,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			compactedRounds?.ids().orEmpty() +
					rounds.flatMap { it.ids() } +
					setOf(summarizedMessage)
		
		/**
		 * 从最早的归档开始遍历整个嵌套结构。
		 */
		fun forEach(block: (CompactedRounds) -> Unit) {
			compactedRounds?.forEach(block)
			block(this)
		}
		
		/**
		 * 将嵌套结构转换为 List，最早的在前，最近的在末尾。
		 *
		 * @return Pair 的 A 为 [summarizedMessage]，B 为 [rounds]。
		 */
		fun toList(): PairList<UUID, List<CompletedRound>> = buildList {
			this@CompactedRounds.forEach {
				add(it.summarizedMessage to it.rounds)
			}
		}
	}
	
	/**
	 * 已完成的轮次，由用户手动终止 Agent 产生，或 LLM 在响应中未调用工具，此时 [finalAssistantMessage] 非空。
	 *
	 * @property userMessage 开启这个轮次的用户消息。
	 * @property turns 已经完成的 [Turn]，参见 [Turn]。
	 * @property finalAssistantMessage LLM 返回的最终消息。
	 */
	@Serializable
	data class CompletedRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		
		@Serializable(with = UuidSerializer::class)
		val finalAssistantMessage: UUID?,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			setOf(userMessage) +
					turns?.flatMap { it.ids() }.orEmpty() +
					setOfNotNull(finalAssistantMessage)
	}
	
	/**
	 * 表示当前正在进行的一个轮次，由一条用户消息开启，LLM 将在一个轮次中进行工具调用 - 推理 - 工具调用的循环。
	 *
	 * 如果 LLM 在一次响应中没有调用任何工具，这个轮次就会被归档为 [CompletedRound]。
	 *
	 * @property userMessage 用户发送的消息，参见 [AgentMessage.User]。
	 * @property assistantMessage 这个字段承载刚刚完成的 LLM 请求，接下来如果未生成工具调用，轮次归档，变为 [CompletedRound.finalAssistantMessage]，如果生成了工具调用（即使无效或失败），[assistantMessage] 连同工具调用的请求和结果都将进入一个 [Turn]，[assistantMessage] / [finishedToolCalls] / [pendingToolCalls] 清空，并继续开始推理。
	 * @property finishedToolCalls 这个字段承载已完成的工具调用，此时仍有 [pendingToolCalls] 或正在进行的工具调用，无法创建 [Turn]。
	 * @property pendingToolCalls 这个字段承载校验通过、等待用户审批的工具调用，这里索引的 [AgentMessage.Tool.Call] 所有字段都必然非空，可在向用户展示后通过 [io.github.autotweaker.api.adapter.AgentAPI.approve] 批准或拒绝。
	 * @property turns 已经完成的 [Turn]，参见 [Turn]。
	 */
	@Serializable
	data class CurrentRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID?,
		val finishedToolCalls: List<Turn.Tool>?,
		val pendingToolCalls: List<@Serializable(with = UuidSerializer::class) UUID>?,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			setOf(userMessage) +
					turns?.flatMap { it.ids() }.orEmpty() +
					setOfNotNull(assistantMessage) +
					finishedToolCalls?.flatMap { it.ids() }.orEmpty() +
					pendingToolCalls.orEmpty()
	}
	
	/**
	 * 一个 Turn 由一次 LLM 思考产生，LLM 在 [assistantMessage] 中发起了 [tools] 中的所有调用，程序处理所有调用并为每一个请求生成响应，并再次调用 LLM。
	 *
	 * @property assistantMessage LLM 的一条消息。
	 * @property tools LLM 的工具请求以及对应的响应。
	 */
	@Serializable
	data class Turn(
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID,
		val tools: List<Tool>,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			setOf(assistantMessage) +
					tools.flatMap { it.ids() }
		
		/**
		 * 表示一条工具调用。
		 *
		 * @property call Agent 的调用请求，参见 [AgentMessage.Tool.Call]。
		 * @property result 工具调用的响应，参见 [AgentMessage.Tool.Result]。
		 */
		@Serializable
		data class Tool(
			@Serializable(with = UuidSerializer::class)
			val call: UUID,
			
			@Serializable(with = UuidSerializer::class)
			val result: UUID,
		) : UuidIndex {
			override fun ids(): Set<UUID> = setOf(call, result)
		}
	}
}
