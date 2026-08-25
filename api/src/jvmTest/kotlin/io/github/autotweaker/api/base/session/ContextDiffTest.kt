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

import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.ContextInjection
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextDiffTest {
	
	private fun context(
		index: AgentContextIndex = AgentContextIndex(null, null, null),
		injections: List<ContextInjection>? = null,
		dropped: Set<UUID>? = null,
	) = AgentContext("prompt", injections, index, dropped)
	
	private fun compacted(
		summary: UUID,
		rounds: List<AgentContextIndex.CompletedRound> = emptyList(),
		inner: AgentContextIndex.CompactedRounds? = null,
	) = AgentContextIndex.CompactedRounds(inner, rounds, summary)
	
	private fun completed(userId: UUID) = AgentContextIndex.CompletedRound(userId, null, null)
	
	private fun current(
		userId: UUID,
		turns: List<AgentContextIndex.Turn>? = null,
		assistant: UUID? = null,
		finished: List<AgentContextIndex.Turn.Tool>? = null,
		pending: List<UUID>? = null,
	) = AgentContextIndex.CurrentRound(userId, turns, assistant, finished, pending)
	
	private fun turn(
		asst: UUID,
		tools: List<AgentContextIndex.Turn.Tool> = emptyList(),
	) = AgentContextIndex.Turn(asst, tools)
	
	private fun tool(
		call: UUID = UUID.randomUUID(),
		result: UUID = UUID.randomUUID(),
	) = AgentContextIndex.Turn.Tool(call, result)
	
	private fun injection(id: UUID = UUID.randomUUID()) =
		ContextInjection(id = id, tag = "tag", content = "content")
	
	// region 基础
	
	@Test
	fun `diff returns null for same instance`() {
		val ctx = context()
		assertNull(ctx diff ctx)
	}
	
	@Test
	fun `diff returns null for equal contents`() {
		val a = context()
		val b = context()
		assertNull(a diff b)
	}
	
	// endregion
	
	// region injections
	
	@Test
	fun `added injections when old null`() {
		val i = injection()
		val diff = context() diff context(injections = listOf(i))
		
		assertNotNull(diff)
		assertEquals(listOf(i), diff.addedInjections())
		assertNull(diff.updatedInjections())
		assertNull(diff.removedInjections())
	}
	
	@Test
	fun `removed injections when new null`() {
		val i = injection()
		val diff = context(injections = listOf(i)) diff context()
		
		assertNotNull(diff)
		assertEquals(listOf(i), diff.removedInjections())
		assertNull(diff.addedInjections())
		assertNull(diff.updatedInjections())
	}
	
	@Test
	fun `updated injections by id`() {
		val id = UUID.randomUUID()
		val old = ContextInjection(id = id, tag = "tag", content = "old")
		val new = ContextInjection(id = id, tag = "tag", content = "new")
		val diff = context(injections = listOf(old)) diff context(injections = listOf(new))
		
		assertNotNull(diff)
		assertEquals(listOf(new), diff.updatedInjections())
		assertNull(diff.addedInjections())
		assertNull(diff.removedInjections())
	}
	
	@Test
	fun `mixed injection changes`() {
		val keepId = UUID.randomUUID()
		val oldKeep = ContextInjection(id = keepId, tag = "tag", content = "same")
		val removed = injection()
		val added = injection()
		val updated = ContextInjection(id = keepId, tag = "tag", content = "changed")
		val diff = context(injections = listOf(oldKeep, removed)) diff
				context(injections = listOf(updated, added))
		
		assertNotNull(diff)
		assertEquals(listOf(added), diff.addedInjections())
		assertEquals(listOf(updated), diff.updatedInjections())
		assertEquals(listOf(removed), diff.removedInjections())
	}
	
	// endregion
	
	// region compactedRounds
	
	@Test
	fun `added compacted rounds from null`() {
		val summary = UUID.randomUUID()
		val rounds = listOf(completed(UUID.randomUUID()))
		val diff = context() diff context(AgentContextIndex(compacted(summary, rounds), null, null))
		
		assertNotNull(diff)
		assertEquals(listOf(summary to rounds), diff.addedCompactedRounds())
	}
	
	@Test
	fun `added compacted rounds appends nested`() {
		val summary1 = UUID.randomUUID()
		val summary2 = UUID.randomUUID()
		val r1 = listOf(completed(UUID.randomUUID()))
		val r2 = listOf(completed(UUID.randomUUID()))
		val old = context(AgentContextIndex(compacted(summary1, r1), null, null))
		val new = context(AgentContextIndex(compacted(summary2, r2, compacted(summary1, r1)), null, null))
		val diff = old diff new
		
		assertNotNull(diff)
		assertEquals(listOf(summary2 to r2), diff.addedCompactedRounds())
	}
	
	// endregion
	
	// region historyRounds
	
	@Test
	fun `added history rounds`() {
		val round = completed(UUID.randomUUID())
		val diff = context() diff context(AgentContextIndex(null, listOf(round), null))
		
		assertNotNull(diff)
		assertEquals(listOf(round), diff.addedHistoryRounds())
		assertNull(diff.removedHistoryRounds())
	}
	
	@Test
	fun `removed history rounds`() {
		val round = completed(UUID.randomUUID())
		val diff = context(AgentContextIndex(null, listOf(round), null)) diff context()
		
		assertNotNull(diff)
		assertEquals(listOf(round), diff.removedHistoryRounds())
		assertNull(diff.addedHistoryRounds())
	}
	
	@Test
	fun `history rounds moved to compacted`() {
		val summary = UUID.randomUUID()
		val r1 = completed(UUID.randomUUID())
		val r2 = completed(UUID.randomUUID())
		val old = context(AgentContextIndex(null, listOf(r1, r2), null))
		val new = context(AgentContextIndex(compacted(summary, listOf(r1)), listOf(r2), null))
		val diff = old diff new
		
		assertNotNull(diff)
		assertEquals(listOf(summary to listOf(r1)), diff.addedCompactedRounds())
		assertEquals(listOf(r1), diff.removedHistoryRounds())
		assertNull(diff.addedHistoryRounds())
	}
	
	// endregion
	
	// region currentRound
	
	@Test
	fun `started round from null`() {
		val userId = UUID.randomUUID()
		val round = current(userId)
		val diff = context() diff context(AgentContextIndex(null, null, round))
		
		assertNotNull(diff)
		assertEquals(round, diff.startedRound())
		assertNull(diff.finishedRound())
		assertNull(diff.updatedCurrent())
	}
	
	@Test
	fun `finished round to null`() {
		val userId = UUID.randomUUID()
		val round = current(userId)
		val diff = context(AgentContextIndex(null, null, round)) diff context()
		
		assertNotNull(diff)
		assertEquals(round, diff.finishedRound())
		assertNull(diff.startedRound())
		assertNull(diff.updatedCurrent())
	}
	
	@Test
	fun `started and finished when user message changes`() {
		val oldRound = current(UUID.randomUUID())
		val newRound = current(UUID.randomUUID())
		val diff = context(AgentContextIndex(null, null, oldRound)) diff
				context(AgentContextIndex(null, null, newRound))
		
		assertNotNull(diff)
		assertEquals(oldRound, diff.finishedRound())
		assertEquals(newRound, diff.startedRound())
		assertNull(diff.updatedCurrent())
	}
	
	@Test
	fun `updated current when same user message`() {
		val userId = UUID.randomUUID()
		val oldRound = current(userId)
		val newRound = current(userId, pending = listOf(UUID.randomUUID()))
		val diff = context(AgentContextIndex(null, null, oldRound)) diff
				context(AgentContextIndex(null, null, newRound))
		
		assertNotNull(diff)
		assertNull(diff.startedRound())
		assertNull(diff.finishedRound())
		assertNotNull(diff.updatedCurrent())
	}
	
	// endregion
	
	// region current diff
	
	@Test
	fun `current diff added turns`() {
		val userId = UUID.randomUUID()
		val t1 = turn(UUID.randomUUID())
		val t2 = turn(UUID.randomUUID())
		val diff = context(AgentContextIndex(null, null, current(userId, turns = listOf(t1)))) diff
				context(AgentContextIndex(null, null, current(userId, turns = listOf(t1, t2))))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(listOf(t2), currentDiff.addedTurns())
		assertNull(currentDiff.newAssistantMessage())
		assertNull(currentDiff.cleanedAssistantMessage())
	}
	
	@Test
	fun `current diff new assistant message`() {
		val userId = UUID.randomUUID()
		val asst = UUID.randomUUID()
		val diff = context(AgentContextIndex(null, null, current(userId))) diff
				context(AgentContextIndex(null, null, current(userId, assistant = asst)))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(asst, currentDiff.newAssistantMessage())
		assertNull(currentDiff.cleanedAssistantMessage())
	}
	
	@Test
	fun `current diff cleaned assistant message`() {
		val userId = UUID.randomUUID()
		val asst = UUID.randomUUID()
		val diff = context(AgentContextIndex(null, null, current(userId, assistant = asst))) diff
				context(AgentContextIndex(null, null, current(userId)))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(asst, currentDiff.cleanedAssistantMessage())
		assertNull(currentDiff.newAssistantMessage())
	}
	
	@Test
	fun `current diff added finished calls`() {
		val userId = UUID.randomUUID()
		val f1 = tool()
		val f2 = tool()
		val diff = context(AgentContextIndex(null, null, current(userId, finished = listOf(f1)))) diff
				context(AgentContextIndex(null, null, current(userId, finished = listOf(f1, f2))))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(listOf(f2), currentDiff.addedFinishedCalls())
		assertNull(currentDiff.removedFinishedCalls())
	}
	
	@Test
	fun `current diff removed finished calls`() {
		val userId = UUID.randomUUID()
		val f1 = tool()
		val f2 = tool()
		val diff = context(AgentContextIndex(null, null, current(userId, finished = listOf(f1, f2)))) diff
				context(AgentContextIndex(null, null, current(userId, finished = listOf(f1))))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(listOf(f2), currentDiff.removedFinishedCalls())
		assertNull(currentDiff.addedFinishedCalls())
	}
	
	@Test
	fun `current diff added pending calls`() {
		val userId = UUID.randomUUID()
		val p1 = UUID.randomUUID()
		val diff = context(AgentContextIndex(null, null, current(userId))) diff
				context(AgentContextIndex(null, null, current(userId, pending = listOf(p1))))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(listOf(p1), currentDiff.addedPendingCalls())
		assertNull(currentDiff.removedPendingCalls())
	}
	
	@Test
	fun `current diff removed pending calls`() {
		val userId = UUID.randomUUID()
		val p1 = UUID.randomUUID()
		val diff = context(AgentContextIndex(null, null, current(userId, pending = listOf(p1)))) diff
				context(AgentContextIndex(null, null, current(userId)))
		
		val currentDiff = diff!!.updatedCurrent()
		assertNotNull(currentDiff)
		assertEquals(listOf(p1), currentDiff.removedPendingCalls())
		assertNull(currentDiff.addedPendingCalls())
	}
	
	@Test
	fun `full round progression diff`() {
		val userId = UUID.randomUUID()
		val asst = UUID.randomUUID()
		val f1 = tool()
		val p1 = UUID.randomUUID()
		val old = context(AgentContextIndex(null, null, current(userId)))
		val new = context(
			AgentContextIndex(
				null,
				null,
				current(userId, assistant = asst, finished = listOf(f1), pending = listOf(p1))
			)
		)
		val diff = old diff new
		
		assertNotNull(diff)
		val currentDiff = diff.updatedCurrent()!!
		assertEquals(asst, currentDiff.newAssistantMessage())
		assertEquals(listOf(f1), currentDiff.addedFinishedCalls())
		assertEquals(listOf(p1), currentDiff.addedPendingCalls())
		assertNull(currentDiff.addedTurns())
	}
	
	// endregion
	
	// region messages
	
	@Test
	fun `added messages difference`() {
		val existing = UUID.randomUUID()
		val added = UUID.randomUUID()
		val old = context(AgentContextIndex(null, listOf(completed(existing)), null))
		val new = context(AgentContextIndex(null, listOf(completed(existing), completed(added)), null))
		val diff = old diff new
		
		assertNotNull(diff)
		assertEquals(setOf(added), diff.addedMessages())
	}
	
	@Test
	fun `dropped messages difference`() {
		val oldDropped = setOf(UUID.randomUUID())
		val newDropped = oldDropped + UUID.randomUUID()
		val diff = context(dropped = oldDropped) diff context(dropped = newDropped)
		
		assertNotNull(diff)
		assertEquals(newDropped - oldDropped, diff.droppedMessages())
	}
	
	@Test
	fun `dropped messages when old null`() {
		val dropped = setOf(UUID.randomUUID())
		val diff = context() diff context(dropped = dropped)
		
		assertNotNull(diff)
		assertEquals(dropped, diff.droppedMessages())
	}
	
	// endregion
}
