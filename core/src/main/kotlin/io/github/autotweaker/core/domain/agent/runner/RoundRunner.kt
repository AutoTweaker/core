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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.agent.AgentContextManager
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.compact.CompactSettings
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.*

class RoundRunner(
	workspace: WorkspaceMeta,
	containerConfig: ContainerConfig,
	private val ctx: AgentContextManager,
	private val tools: Tools,
	private val thinkingStage: ThinkingStage,
	toolCalling: ToolCallingStage,
	private val compactService: CompactService,
	model: Model,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	private val service: SettingService,
	private val statusFlow: MutableStateFlow<AgentStatus>,
	private val agentId: UUID,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val mutex = Mutex()
	
	private val messages = MessageQueue(agentId)
	private val approval =
		ApprovalProcessor(ctx, toolCalling, ToolResultFactory(service), workspace, containerConfig, scope)
	private val roundCtx = RoundContext(ctx, ToolResultFactory(service))
	
	@Volatile
	private var thinkJob: Job? = null
	
	@Volatile
	private var compactJob: Job? = null
	
	@Volatile
	private var shouldBreak = false
	
	@Volatile
	var currentModel = model
	
	@Volatile
	var currentSummarizeModel = summarizeModel
	
	@Volatile
	var currentFallbackModels = fallbackModels
	
	@Volatile
	var currentThinking: Boolean = thinking
	
	fun send(content: MessageContent) = messages.send(content)
	
	fun start() {
		scope.launch { workLoop() }
	}
	
	fun shutdown() {
		scope.cancel()
	}
	
	suspend fun execute(command: AgentCommand) = mutex.withLock {
		when (command) {
			is AgentCommand.Stop -> {
				check(statusFlow.value != AgentStatus.FREE)
				markBreak()
				thinkJob?.cancel()
				approval.cancelToolJob()
				statusFlow.first { it == AgentStatus.FREE }
			}
			
			is AgentCommand.Pause -> {
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
				command.summarizeModel?.let { currentSummarizeModel = it }
				command.fallbackModels?.let { currentFallbackModels = it }
				command.thinking?.let { currentThinking = it }
			}
			
			is AgentCommand.ApproveTool -> {
				check(statusFlow.value == AgentStatus.WAITING)
				approval.approvalChannel.send(command.approval)
			}
		}
		logger.debug("Processed command  command={}  agentId={}", command::class.simpleName, agentId)
	}
	
	private suspend fun workLoop() {
		logger.info("Started workLoop  agentId={}", agentId)
		while (true) {
			val msg = messages.receive()
			shouldBreak = false
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
					fallbackModels = currentFallbackModels,
					thinking = currentThinking,
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
			
			if (shouldBreak || result is ThinkingStage.Result.Failed) break
			
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
					currentSummarizeModel,
					currentFallbackModels,
					statusFlow,
				) { shouldBreak }
				messages.sendReasons(reasons)
			}
			
			ctx.finalizeToolTurn()
			
			if (shouldBreak) break
			
			checkAutoDeactivate()
			checkAutoCompact()
			
			val drained = messages.drain()
			if (drained != null) {
				ctx.archiveCurrentRound()
				ctx.beginRound(drained)
			}
		}
		logger.info("Completed round execution  agentId={}", agentId)
	}
	
	private suspend fun checkAutoDeactivate() {
		val threshold = service.get(AgentToolSettings.DeactivationThreshold()).value
		if (threshold <= 0) return
		val history = ctx.get().let { context ->
			(context.historyRounds ?: emptyList()) + (context.compactedRounds?.flatMap { it.rounds } ?: emptyList())
		}
		if (history.isEmpty()) return
		val allCalls = history.flatMap { round ->
			round.turns?.flatMap { turn -> turn.tools.map { it.name.substringBefore("-") } } ?: emptyList()
		}
		if (allCalls.size < threshold) return
		val recentNames = allCalls.takeLast(threshold).toSet()
		
		tools.toolInfo.value.filter { it.active }.forEach { info ->
			if (info.name !in recentNames)
				tools.activate(info.name, false)
		}
	}
	
	private suspend fun checkAutoCompact() {
		if (compactJob?.isActive == true) return
		val usage = ctx.get().let { context ->
			context.currentRound?.assistantMessage?.usageSnapshot?.usage
				?: context.currentRound?.turns?.lastOrNull()?.assistantMessage?.usageSnapshot?.usage
				?: context.historyRounds?.lastOrNull()?.finalAssistantMessage?.usageSnapshot?.usage
		} ?: return
		val contextWindow = currentModel.modelInfo.contextWindow
		val config = currentModel.config
		
		val contextUsageThreshold = config?.compactContextUsage
			?: service.get(CompactSettings.DefaultCompactContextUsage()).value
				.takeIf { it > 0.0 && it <= 1.0 }
		val totalTokensThreshold = config?.compactTotalTokens
			?: service.get(CompactSettings.DefaultCompactTotalTokens()).value
				.takeIf { it > 0 }
		
		val exceedContextUsage = contextUsageThreshold != null &&
				usage.totalTokens.toDouble() / contextWindow >= contextUsageThreshold
		val exceedTotalTokens = totalTokensThreshold != null &&
				usage.totalTokens >= totalTokensThreshold
		if (exceedContextUsage || exceedTotalTokens) {
			logger.debug(
				"Triggered auto-compact  agentId={}  totalTokens={}  contextWindow={}",
				agentId,
				usage.totalTokens,
				contextWindow
			)
			launchCompact()
		}
	}
	
	private fun activeAll(activations: List<ToolActivation>) =
		activations.forEach { tools.activate(it.toolCall.name, true) }
	
	private fun launchCompact() {
		if (compactJob?.isActive == true) return
		compactJob = scope.launch {
			compactService.execute(currentSummarizeModel, currentFallbackModels, service, ctx)
		}
	}
	
	private fun markBreak() {
		shouldBreak = true
	}
}
