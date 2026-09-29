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
import io.github.autotweaker.api.types.message.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * 表示 Agent 的上下文，不持有任何消息，而是使用 [UUID] 索引。在 [AgentContextIndex] 中，所有的 [List] 只要非 null 就必有元素。
 *
 * 在 AgentContext 的模型中，每条用户消息都会开启一个独立的轮次，每个轮次中 Agent 可能进行多轮工具调用，这些中间的工具调用称为 Turn。
 *
 * 每个 Turn 记录一条 Agent 消息和若干工具请求以及响应，Agent 在下一个 Turn 思考并继续调用工具。
 *
 * 如果 Agent 没有调用工具，Turn 将不会被创建，Agent 的消息将作为 finalAssistantMessage，[currentRound] 会被归档至 [historyRounds]。
 *
 * @property compactedRounds 压缩自 [historyRounds] 的消息，只有一条 summary 进入实际上下文。
 * @property historyRounds 已完成的轮次，由用户手动终止 Agent 产生，或 LLM 在响应中未调用工具，此时 [Round.assistantMsgRef] 非空。
 * @property currentRound 当前正在进行的一个轮次，由一条用户消息开启，LLM 将在一个轮次中进行工具调用 - 推理 - 工具调用的循环。
 */
@Serializable
data class AgentContextIndex(
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<Round>?,
	val currentRound: Round?,
) : UuidIndex() {
	override fun <C : MutableCollection<UUID>> idsTo(destination: C) = destination.apply {
		compactedRounds?.idsTo(this)
		historyRounds?.forEach { it.idsTo(this) }
		currentRound?.idsTo(this)
	}
	
	/**
	 * 上下文压缩产生的归档，包含 summary 和 summary 覆盖的若干 [Round]。
	 *
	 * 上下文压缩后，[AgentContextIndex.compactedRounds] 会更新，[AgentContextIndex.historyRounds] 中的相关轮次会被移动到此。
	 *
	 * @property compactedRounds 上下文压缩运行时会包含上次的 summary 进行总结，此字段刚好引用了更早的归档。
	 * @property rounds 上下文压缩覆盖的历史轮次，来自 historyRounds。
	 * @property summaryMsgRef LLM 生成的总结消息，参见 [io.github.autotweaker.api.types.message.AgentMessage.Compact]。
	 */
	@Serializable
	data class CompactedRounds(
		val compactedRounds: CompactedRounds?,
		val rounds: List<Round>,
		val summaryMsgRef: CompactRef,
	) : UuidIndex() {
		val summaryMessage by summaryMsgRef
		override fun <C : MutableCollection<UUID>> idsTo(destination: C): C = destination.apply {
			compactedRounds?.idsTo(this)
			rounds.forEach { it.idsTo(this) }
			add(summaryMsgRef.id)
		}
		
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
		 * @return Pair 的 A 为 [summaryMsgRef]，B 为 [rounds]。
		 */
		fun toList(): PairList<UUID, List<Round>> = buildList {
			this@CompactedRounds.forEach {
				add(it.summaryMsgRef.id to it.rounds)
			}
		}
	}
	
	/**
	 * 轮次，由一条用户消息开启，以用户手动终止、新用户消息（开启新轮次，结束当前轮次）或 LLM 在一次响应中未调用工具结束。
	 *
	 * 如果 LLM 在一次响应中没有调用任何工具，这个轮次就会被归档为 [historyRounds]。
	 *
	 * @property userMsgRef 开启这个轮次的用户消息，参见 [io.github.autotweaker.api.types.message.AgentMessage.User]。
	 * @property turns 已经完成的 [Turn]，参见 [Turn]。
	 * @property assistantMessage LLM 返回的最终消息。对于 [currentRound]，这个字段承载刚刚完成的 LLM 请求，接下来如果未生成工具调用，轮次归档，此字段不变；如果生成了工具调用（即使无效或失败），在调用完成后 [assistantMsgRef] 连同工具调用的请求和结果都将进入一个 [Turn]，[assistantMsgRef] 置空，并继续开始推理。对于 [currentRound]，此字段非空时 [io.github.autotweaker.api.adapter.Agent.toolCalls] 也非空。
	 */
	@Serializable
	data class Round(
		val userMsgRef: UserRef,
		val turns: List<Turn>?,
		val assistantMsgRef: AssistantRef?,
	) : UuidIndex() {
		val userMessage by userMsgRef
		val assistantMessage by assistantMsgRef
		override fun <C : MutableCollection<UUID>> idsTo(destination: C) = destination.apply {
			add(userMsgRef.id)
			turns?.forEach { it.idsTo(this) }
			assistantMsgRef?.let { add(it.id) }
		}
	}
	
	/**
	 * 一个 Turn 由一次 LLM 思考产生，LLM 在 [assistantMsgRef] 中发起了 [tools] 中的所有调用，程序处理所有调用并为每一个请求生成响应，并再次调用 LLM。
	 *
	 * @property assistantMessage LLM 的一条消息。
	 * @property tools LLM 的工具请求以及对应的响应。
	 */
	@Serializable
	data class Turn(
		val assistantMsgRef: AssistantRef,
		val tools: List<Tool>,
	) : UuidIndex() {
		val assistantMessage by assistantMsgRef
		override fun <C : MutableCollection<UUID>> idsTo(destination: C) = destination.apply {
			add(assistantMsgRef.id)
			tools.forEach { it.idsTo(this) }
		}
		
		/**
		 * 表示一条工具调用。
		 *
		 * @property call Agent 的调用请求，参见 [io.github.autotweaker.api.types.message.AgentMessage.Tool.Call]。
		 * @property result 工具调用的响应，参见 [io.github.autotweaker.api.types.message.AgentMessage.Tool.Result]。
		 */
		@Serializable
		data class Tool(
			val callRef: ToolCallRef,
			val resultRef: ToolResultRef,
		) : UuidIndex() {
			val call by callRef
			val result by resultRef
			override fun <C : MutableCollection<UUID>> idsTo(destination: C) = destination.apply {
				add(callRef.id)
				add(resultRef.id)
			}
		}
	}
	
	companion object {
		val EMPTY = AgentContextIndex(
			compactedRounds = null, historyRounds = null, currentRound = null
		)
	}
}
