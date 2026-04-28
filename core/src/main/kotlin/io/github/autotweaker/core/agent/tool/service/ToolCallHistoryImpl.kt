package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.tool.impl.read.ToolCallHistory

@Suppress("unused")
class ToolCallHistoryImpl(
	context: AgentContext,
) : ToolCallHistory {
	private val entries: List<ToolCallHistory.Entry> = buildList {
		for (round in context.historyRounds.orEmpty()) {
			for (turn in round.turns.orEmpty()) {
				for (tool in turn.tools) {
					add(
						ToolCallHistory.Entry(
							name = tool.name,
							arguments = tool.call.arguments,
							resultContent = tool.result.content,
						)
					)
				}
			}
		}
		for (turn in context.currentRound?.turns.orEmpty()) {
			for (tool in turn.tools) {
				add(
					ToolCallHistory.Entry(
						name = tool.name,
						arguments = tool.call.arguments,
						resultContent = tool.result.content,
					)
				)
			}
		}
	}
	
	override fun getAll(): List<ToolCallHistory.Entry> = entries
}
