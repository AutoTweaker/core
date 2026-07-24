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

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class RoundRunner(
	private val ctx: AgentContextManager,
	private val tools: Tools,
	private val thinkingStage: ThinkingStage,
	private val toolCalling: ToolCallingStage,
	private val compactService: CompactService,
	agentModel: AgentModel,
	private val statusFlow: MutableStateFlow<AgentStatus>,
	private val agentId: UUID,
) : Loggable, Traceable, Settable {
	private val scope = scope()
	private val cmdLock = ReentrantMutex()
	private val compactLock = ReentrantMutex()
	
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
		scope, shouldBreak
	)
	private val roundCtx = RoundContext(ctx, ToolResultFactory())
	
	init {
		scope.launch { workLoop() }
	}
	
	suspend fun shutdown() {
		shutdownStarted = true
		compactJob?.cancel()
		execute(AgentCommand.Stop)
		scope.cancel()
		messages.shutdown()
	}
	
	suspend fun execute(command: AgentCommand) = also {
		cmdLock.withLock {
			if (command !is AgentCommand.Stop && shutdownStarted) return@withLock
			when (command) {
				is AgentCommand.Stop -> {
					if (statusFlow.value == AgentStatus.FREE) return@withLock
					markBreak()
					thinkJob?.cancel()
					toolCalling.cancelToolJob()
					statusFlow.first { it == AgentStatus.FREE }
				}
				
				is AgentCommand.Pause -> {
					if (statusFlow.value == AgentStatus.FREE) return@withLock
					markBreak()
					statusFlow.first { it == AgentStatus.FREE }
				}
				
				is AgentCommand.CancelTool ->
					toolCalling.cancelToolJob()
				
				
				is AgentCommand.CancelCompact ->
					compactJob?.cancel()
				
				
				is AgentCommand.Compact ->
					launchCompact()
				
				
				is AgentCommand.UpdateModel ->
					currentModel = command.model
				
				
				is AgentCommand.ApproveTool -> {
					check(statusFlow.value == AgentStatus.WAITING)
					approval.approvalChannel.send(command.approval)
				}
			}.andLog(log) {
				debug("Processed command  command={}  agentId={}", command::class.simpleName, agentId)
			}
		}
	}
	
	fun send(content: MessageContent): Delivery = messages.send(content)
	
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
		var emptyResponseRetries = 0
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
			
			val result = trace.catching {
				deferred.await()
			}.rethrow<SecretStoreLockedException>()
				.getOrElse { ThinkingStage.Result.Failed }
			thinkJob = null
			
			if (shouldBreak.value || result is ThinkingStage.Result.Failed) break
			
			if (result is ThinkingStage.Result.Done) {
				roundCtx.applyDone(result)
				
				if (result.activations.isEmpty() && result.parseFailures.isEmpty()) {
					messages.drain()?.let {
						ctx.archiveCurrentRound()
						ctx.beginRound(it)
						continue
					}
					
					if (result.assistantMessage.content.isNullOrBlank()
						&& setting(EmptyResponseFeedback())
						&& emptyResponseRetries <= setting(EmptyResponseFeedbackRetries())
					) {
						messages.send(
							ContextInjection(
								"system_reminder",
								setting(EmptyResponseFeedbackPrompt())
							)
						)
						ctx.archiveCurrentRound()
						ctx.beginRound(messages.receive())
						emptyResponseRetries++
						continue
					}
					
					break
				}
				activeAll(result.activations)
			}
			
			if (result is ThinkingStage.Result.HasPending) {
				roundCtx.applyHasPending(result)
				activeAll(result.activations)
				
				val reasons = approval.process(
					result.needsApproval,
					currentModel,
					statusFlow,
				)
				messages.send(reasons)
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
		val threshold = setting(AgentToolSettings.DeactivationThreshold())
		if (threshold <= 0) return
		val history = ctx.get().let { context ->
			context.historyRounds.orEmpty() + context.compactedRounds?.completedRounds().orEmpty()
		}
		if (history.isEmpty()) return
		val allCalls = history.flatMap { round ->
			round.turns?.flatMap { turn ->
				turn.tools.mapNotNull {
					it.call.validatedToolName
				}
			}.orEmpty()
		}
		if (allCalls.size < threshold) return
		val recentNames = allCalls.takeLast(threshold).toSet()
		
		tools.activeTools.value.forEach {
			if (it !in recentNames) tools.activate(it, false)
		}
	}
	
	private suspend fun autoCompact() = compactLock.withLock {
		if (compactJob?.isActive == true) return@withLock
		
		val assistantMessage = ctx.get().let { context ->
			context.currentRound?.assistantMessage
				?: context.currentRound?.turns?.lastOrNull()?.assistantMessage
				?: context.historyRounds?.lastOrNull()?.finalAssistantMessage
		} ?: return@withLock
		val usage = assistantMessage.usageSnapshot?.usage ?: return@withLock
		val contextWindow = assistantMessage.usageSnapshot.model.contextWindow
		val config = currentModel.all().find { it.id == assistantMessage.modelId }?.config
		
		val contextUsageThreshold = config?.compactContextUsage
			?: setting(CompactSettings.DefaultCompactContextUsage())
				.takeIf { it > 0.0 && it <= 1.0 }
		val totalTokensThreshold = config?.compactTotalTokens
			?: setting(CompactSettings.DefaultCompactTotalTokens())
				.takeIf { it > 0 }
		
		val exceedContextUsage = contextUsageThreshold != null &&
				usage.totalTokens.toDouble() / contextWindow >= contextUsageThreshold
		val exceedTotalTokens = totalTokensThreshold != null &&
				usage.totalTokens >= totalTokensThreshold
		if (exceedContextUsage || exceedTotalTokens)
			launchCompact().andLog(log) {
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
	
	private suspend fun launchCompact() = compactLock.withLock {
		if (compactJob?.isActive == true) return@withLock
		compactJob = scope.launch {
			compactService.execute(currentModel, ctx)
		}
	}
	
	private fun markBreak() {
		shouldBreak.value = true
	}
	
	@AutoService(SettingDef::class)
	class EmptyResponseFeedback : BooleanSetting(
		true, zh("是否在模型输出为空时自动发送提示")
	)
	
	@AutoService(SettingDef::class)
	class EmptyResponseFeedbackRetries : IntSetting(
		10, zh("同一轮次内模型输出为空时自动发送提示的最大次数")
	)
	
	@AutoService(SettingDef::class)
	class EmptyResponseFeedbackPrompt : StringSetting(
		"你并没有输出任何有效内容，如果任务仍未完成，请继续工作，否则请输出有效的正文",
		zh("模型输出为空时自动发送的提示词")
	)
}
