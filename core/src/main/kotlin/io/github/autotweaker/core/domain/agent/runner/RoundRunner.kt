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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.agent.AgentContextManager
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentModel.Companion.all
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.compact.CompactSettings
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.config.Settable
import io.github.autotweaker.api.config.setting
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.trace

class RoundRunner(
	workspace: WorkspaceMeta,
	containerConfig: ContainerConfig,
	private val ctx: AgentContextManager,
	private val tools: Tools,
	private val thinkingStage: ThinkingStage,
	toolCalling: ToolCallingStage,
	private val compactService: CompactService,
	agentModel: AgentModel,
	private val statusFlow: MutableStateFlow<AgentStatus>,
	private val agentId: UUID,
) : Loggable, Traceable, Settable {
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val mutex = Mutex()
	
	@Volatile
	private var thinkJob: Job? = null
	
	@Volatile
	private var compactJob: Job? = null
	
	private val shouldBreak = MutableStateFlow(false)
	
	@Volatile
	private var shutdownStarted = false
	
	@Volatile
	private var currentModel = agentModel
	val model = currentModel
	
	private val messages = MessageQueue(agentId)
	private val approval = ApprovalProcessor(
		ctx, toolCalling,
		ToolResultFactory(),
		workspace, containerConfig, scope, shouldBreak
	)
	private val roundCtx = RoundContext(ctx, ToolResultFactory())
	
	fun send(content: MessageContent) = also {
		messages.send(content)
	}
	
	fun start() = also {
		scope.launch { workLoop() }
	}
	
	suspend fun shutdown() = also {
		shutdownStarted = true
		compactJob?.cancel()
		execute(AgentCommand.Stop)
		scope.cancel()
	}
	
	suspend fun execute(command: AgentCommand) = also {
		mutex.withLock {
			if (command !is AgentCommand.Stop && shutdownStarted) return@withLock
			when (command) {
				is AgentCommand.Stop -> {
					if (statusFlow.value == AgentStatus.FREE) return@withLock
					markBreak()
					thinkJob?.cancel()
					approval.cancelToolJob()
					statusFlow.first { it == AgentStatus.FREE }
				}
				
				is AgentCommand.Pause -> {
					if (statusFlow.value == AgentStatus.FREE) return@withLock
					markBreak()
					statusFlow.first { it == AgentStatus.FREE }
				}
				
				is AgentCommand.CancelTool -> {
					approval.cancelToolJob()
				}
				
				is AgentCommand.CancelCompact -> {
					compactJob?.cancel()
				}
				
				is AgentCommand.Compact -> {
					launchCompact()
				}
				
				is AgentCommand.UpdateModel -> {
					currentModel = command.model
				}
				
				is AgentCommand.ApproveTool -> {
					check(statusFlow.value == AgentStatus.WAITING)
					approval.approvalChannel.send(command.approval)
				}
			}
			log.debug("Processed command  command={}  agentId={}", command::class.simpleName, agentId)
		}
	}
	
	private suspend fun workLoop() {
		log.info("Started workLoop  agentId={}", agentId)
		while (true) {
			val msg = messages.receive()
			shouldBreak.value = false
			ctx.beginRound(msg)
			executeRound()
			ctx.archiveCurrentRound()
			statusFlow.value = AgentStatus.FREE
		}
	}
	
	private suspend fun executeRound() {
		while (true) {
			statusFlow.value = AgentStatus.THINKING
			val deferred = scope.async {
				thinkingStage.execute(
					model = currentModel,
					assembledTools = tools.assembleTools(),
					context = ctx.get(),
				)
			}
			thinkJob = deferred
			val result = try {
				deferred.await()
			} catch (e: CancellationException) {
				trace.exception(e)
				thinkJob = null
				break
			}
			thinkJob = null
			
			if (shouldBreak.value || result is ThinkingStage.Result.Failed) break
			
			if (result is ThinkingStage.Result.Done) {
				roundCtx.applyDone(result)
				if (result.activations.isEmpty() && result.parseFailures.isEmpty()) break
				activeAll(result.activations)
			}
			
			if (result is ThinkingStage.Result.HasPending) {
				roundCtx.applyHasPending(result)
				activeAll(result.activations)
				
				val reasons = approval.process(
					result.needsApproval,
					result.assistantMessage.id,
					currentModel,
					statusFlow,
				)
				messages.sendReasons(reasons)
			}
			
			ctx.finalizeToolTurn()
			
			if (shouldBreak.value) break
			
			autoDeactivate()
			autoCompact()
			
			if (shouldBreak.value) break
			
			messages.drain()?.let {
				ctx.archiveCurrentRound()
				ctx.beginRound(it)
			}
		}
		log.info("Completed round execution  agentId={}", agentId)
	}
	
	private suspend fun autoDeactivate() {
		val threshold = setting.get(AgentToolSettings.DeactivationThreshold()).value
		if (threshold <= 0) return
		val history = ctx.get().let { context ->
			context.historyRounds.orEmpty() + context.compactedRounds?.flatMap { it.rounds }.orEmpty()
		}
		if (history.isEmpty()) return
		val allCalls = history.flatMap { round ->
			round.turns?.flatMap { turn -> turn.tools.map { it.name.substringBefore("-") } }.orEmpty()
		}
		if (allCalls.size < threshold) return
		val recentNames = allCalls.takeLast(threshold).toSet()
		
		tools.toolInfo.value.filter { it.active }.forEach { info ->
			if (info.name !in recentNames)
				tools.activate(info.name, false)
		}
	}
	
	private suspend fun autoCompact() {
		if (compactJob?.isActive == true) return
		val assistantMessage = ctx.get().let { context ->
			context.currentRound?.assistantMessage
				?: context.currentRound?.turns?.lastOrNull()?.assistantMessage
				?: context.historyRounds?.lastOrNull()?.finalAssistantMessage
		} ?: return
		val usage = assistantMessage.usageSnapshot?.usage ?: return
		val contextWindow = assistantMessage.usageSnapshot.model.contextWindow
		val config = currentModel.all().find { it.id == assistantMessage.modelId }?.config
		
		val contextUsageThreshold = config?.compactContextUsage
			?: setting.get(CompactSettings.DefaultCompactContextUsage()).value
				.takeIf { it > 0.0 && it <= 1.0 }
		val totalTokensThreshold = config?.compactTotalTokens
			?: setting.get(CompactSettings.DefaultCompactTotalTokens()).value
				.takeIf { it > 0 }
		
		val exceedContextUsage = contextUsageThreshold != null &&
				usage.totalTokens.toDouble() / contextWindow >= contextUsageThreshold
		val exceedTotalTokens = totalTokensThreshold != null &&
				usage.totalTokens >= totalTokensThreshold
		if (exceedContextUsage || exceedTotalTokens) launchCompact().andLog(log) {
			debug(
				"Triggered auto-compact  agentId={}  totalTokens={}  contextWindow={}",
				agentId,
				usage.totalTokens,
				contextWindow
			)
		}
	}
	
	private fun activeAll(activations: List<ToolActivation>) =
		activations.forEach { tools.activate(it.toolCall.name, true) }
	
	private fun launchCompact() {
		if (compactJob?.isActive == true) return
		compactJob = scope.launch {
			compactService.execute(currentModel, ctx)
		}
	}
	
	private fun markBreak() {
		shouldBreak.value = true
	}
}