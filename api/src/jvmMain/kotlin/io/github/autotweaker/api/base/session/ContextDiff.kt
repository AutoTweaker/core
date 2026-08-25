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

package io.github.autotweaker.api.base.session

import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.api.types.agent.AgentContextIndex.*
import io.github.autotweaker.api.types.agent.ContextInjection
import java.util.*

interface ContextDiff {
	/**
	 * 新增的 [ContextInjection]。
	 */
	fun addedInjections(): List<ContextInjection>?
	
	/**
	 * 原来已有，但更新了的 [ContextInjection]。
	 *
	 * @return 更新后的 [ContextInjection]。
	 */
	fun updatedInjections(): List<ContextInjection>?
	
	/**
	 * 被移除的 [ContextInjection]。
	 */
	fun removedInjections(): List<ContextInjection>?
	
	/**
	 * 新增的 [io.github.autotweaker.api.types.agent.AgentContextIndex.CompactedRounds]。
	 *
	 * 这些 [CompletedRound] 必然从 historyRounds 中消失。
	 *
	 * @return Pair 的 A 为 summarizedMessage，B 为此次总结的所有轮次。
	 */
	fun addedCompactedRounds(): PairList<UUID, List<CompletedRound>>?
	
	/**
	 * 新增的 [CompletedRound]，必然来自 currentRound。
	 */
	fun addedHistoryRounds(): List<CompletedRound>?
	
	/**
	 * 减少的 historyRounds，必然出现在 [addedCompactedRounds] 中。
	 */
	fun removedHistoryRounds(): List<CompletedRound>?
	
	/**
	 * 新增的 [CurrentRound]，全新创建。
	 *
	 * [CurrentRound] 在新旧都存在，但实际不是同一个，将返回当前版本。
	 *
	 * 这也是 [finishedRound] 与 [startedRound] 同时返回非空的唯一情况。
	 */
	fun startedRound(): CurrentRound?
	
	/**
	 * 更新了 [CurrentRound]，无 [CurrentRound] 返回 null。
	 *
	 * [CurrentRound] 在新旧都存在，但实际不是同一个，也返回 null。
	 */
	fun updatedCurrent(): CurrentDiff?
	
	/**
	 * 完成的 [CurrentRound]，除非因为轮次为空被丢弃，必然进入 historyRounds。
	 *
	 * [CurrentRound] 在新旧都存在，但实际不是同一个，将返回旧版本。
	 *
	 * 这也是 [finishedRound] 与 [startedRound] 同时返回非空的唯一情况。
	 */
	fun finishedRound(): CurrentRound?
	
	/**
	 * 新增的消息。
	 */
	fun addedMessages(): Set<UUID>?
	
	/**
	 * 被丢弃的消息，两次 [io.github.autotweaker.api.types.agent.AgentContext.droppedMessages] 的差集。
	 */
	fun droppedMessages(): Set<UUID>?
	
	/**
	 * 参考 [io.github.autotweaker.api.types.agent.AgentContextIndex] 的 [CurrentRound]。
	 */
	interface CurrentDiff {
		/**
		 * 有新 [Turn]。
		 */
		fun addedTurns(): List<Turn>?
		
		/**
		 * 新的 assistantMessage。
		 */
		fun newAssistantMessage(): UUID?
		
		/**
		 * assistantMessage 被归档至 turns。
		 */
		fun cleanedAssistantMessage(): UUID?
		
		fun addedFinishedCalls(): List<Turn.Tool>?
		fun removedFinishedCalls(): List<Turn.Tool>?
		fun addedPendingCalls(): List<UUID>?
		fun removedPendingCalls(): List<UUID>?
	}
}
