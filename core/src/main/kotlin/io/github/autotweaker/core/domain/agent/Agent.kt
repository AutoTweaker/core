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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.phase.*
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Instant

class Agent(
	context: AgentContext,
	override val workspace: WorkspaceMeta,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	override val summarizeModel: Model,
	override val containerConfig: ContainerConfig,
	override val service: SettingService,
	tools: List<Tool>,
) : AgentEnvironment {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	override val agentId: UUID = UUID.randomUUID()
	override val toolCancelledMessage = service.get(AgentToolSettings.Cancelled()).value
	override val toolRejectedMessage = service.get(AgentToolSettings.Rejected()).value
	override val toolRejectedWithFeedbackMessage = service.get(AgentToolSettings.RejectedWithFeedback()).value
	
	//工具状态
	override val agentState = MutableAgentState()
	
	private val _context = MutableStateFlow(context)
	override val context: StateFlow<AgentContext> = _context.asStateFlow()
	
	override suspend fun updateContext(transform: suspend (AgentContext) -> AgentContext) {
		_context.update { transform(it) }
	}
	
	//工具列表
	override val tools = Tools(service).also { t -> tools.forEach { t.add(it) } }
	
	//模型数据
	override var currentModel: Model = model
	override var currentFallbackModels: List<Model>? = fallbackModels
	override var currentThinking: Boolean = thinking
	
	//当前工作协程
	private var currentJob: Job? = null
	private var compactJob: Job? = null
	
	//状态
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val statusFlow: StateFlow<AgentStatus> = _status.asStateFlow()
	
	//输出
	private val _output = MutableSharedFlow<AgentOutput>()
	val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	//双通道
	private val directiveChannel = Channel<AgentCommand.Directive>(Channel.UNLIMITED)
	private val messageChannel = Channel<AgentCommand.Message>(Channel.UNLIMITED)
	
	//工作触发信号
	private val workTrigger = Channel<Unit>(Channel.CONFLATED)
	
	//协程
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	//状态
	override val status: AgentStatus get() = _status.value
	
	//输出
	override suspend fun emitOutput(output: AgentOutput) {
		_output.emit(output)
	}
	
	//更新状态
	override fun updateStatus(status: AgentStatus) {
		logger.debug("Agent status changed  agentId={}  from={}  to={}", agentId, _status.value, status)
		_status.value = status
	}
	
	//初始化
	init {
		logger.info(
			"Agent created  agentId={}  model={}  fallbackModels={}  thinking={}",
			agentId,
			model.modelInfo.modelId,
			fallbackModels?.map { it.modelInfo.modelId },
			thinking
		)
		startEventLoop()
	}
	
	//启动协程
	private fun startEventLoop() {
		//命令处理
		scope.launch {
			while (isActive) {
				val directive = directiveChannel.tryReceive().getOrNull()
				if (directive != null) {
					handleDirective(directive)
					continue
				}
				select {
					directiveChannel.onReceive { handleDirective(it) }
					messageChannel.onReceive { handleMessage(it) }
				}
			}
		}
		//工作触发信号
		scope.launch {
			while (true) {
				workTrigger.receive()
				resumeFromCurrentState()
			}
		}
		logger.debug("Event loop started  agentId={}", agentId)
	}
	
	//处理指令（即时
	private suspend fun handleDirective(directive: AgentCommand.Directive) {
		when (directive) {
			is AgentCommand.Directive.Stop -> {
				logger.info("Stop requested  agentId={}", agentId)
				currentJob?.cancel()
				currentJob = null
				compactJob?.cancel()
				compactJob = null
				ContextPhase.archiveCurrentRound(this, this::updateContext)
				updateStatus(AgentStatus.FREE)
				scope.cancel()
				logger.info("Agent stopped  agentId={}", agentId)
			}
			
			is AgentCommand.Directive.UpdateModel -> {
				currentModel = directive.model
				directive.fallbackModels?.let { currentFallbackModels = it }
				directive.thinking?.let { currentThinking = it }
				logger.info(
					"Model updated  agentId={}  model={}  fallbackModels={}  thinking={}",
					agentId,
					directive.model.modelInfo.modelId,
					directive.fallbackModels?.map { it.modelInfo.modelId },
					directive.thinking
				)
			}
			
			is AgentCommand.Directive.Pause -> {
				if (_status.value == AgentStatus.FREE || _status.value == AgentStatus.ERROR || _status.value == AgentStatus.PAUSED || _status.value == AgentStatus.WAITING) return
				logger.info("Pause requested  agentId={}  status={}", agentId, _status.value)
				updateStatus(AgentStatus.PAUSED)
			}
			
			is AgentCommand.Directive.Resume -> {
				if (_status.value != AgentStatus.PAUSED) return
				logger.info("Resume requested  agentId={}", agentId)
				updateStatus(AgentStatus.FREE)
				workTrigger.trySend(Unit)
			}
			
			is AgentCommand.Directive.Cancel -> {
				logger.info("Cancel requested  agentId={}  status={}", agentId, _status.value)
				if (_status.value == AgentStatus.TOOL_CALLING) {
					currentJob?.cancel()
				}
				compactJob?.cancel()
				compactJob = null
			}
			
			is AgentCommand.Directive.Retry -> {
				if (_status.value != AgentStatus.ERROR) return
				logger.info("Retried from error  agentId={}", agentId)
				updateStatus(AgentStatus.FREE)
				workTrigger.trySend(Unit)
			}
			
			is AgentCommand.Directive.Compact -> {
				logger.debug("Compact requested  agentId={}", agentId)
				launchCompact()
			}
		}
	}
	
	//处理消息
	private suspend fun handleMessage(message: AgentCommand.Message) {
		when (message) {
			is AgentCommand.Message.SendMessage -> {
				if (_status.value != AgentStatus.FREE) {
					logger.warn(
						"Dropped user message  reason=agent_not_free  agentId={}  status={}  messageId={}",
						agentId,
						_status.value,
						message.id
					)
					return
				}
				logger.debug(
					"Received user message  agentId={}  messageId={}  charCount={}",
					agentId,
					message.id,
					message.content.length
				)
				processUserMessage(message.id, message.content, message.images, message.timestamp)
			}
			
			is AgentCommand.Message.ApproveToolCall -> {
				if (_status.value != AgentStatus.WAITING) {
					messageChannel.trySend(message)
					return
				}
				if (agentState.pendingApproval == null) return
				logger.debug("Tool approvals processed  agentId={}  approvalCount={}", agentId, message.approvals.size)
				val result = HandleApprovalPhase.execute(this@Agent, message.approvals) { result, call ->
					scope.async {
						ExecuteToolPhase.execute(this@Agent, result, call)
					}.also { currentJob = it }.await()
				}
				when (result) {
					PhaseResult.Continue -> workTrigger.trySend(Unit)
					PhaseResult.Done -> {}
					PhaseResult.Error -> logger.warn("Failed to execute tool approval phase  agentId={}", agentId)
				}
			}
		}
	}
	
	//处理用户消息
	private suspend fun processUserMessage(
		id: UUID, content: String, images: List<Base64>? = null, timestamp: Instant
	) {
		val userMsg = AgentContext.Message.User(
			id = id, content = content, images = images, timestamp = timestamp
		)
		updateContext {
			it.copy(currentRound = AgentContext.CurrentRound(userMessage = userMsg, turns = null))
		}
		logger.debug("User message processed  agentId={}  messageId={}", agentId, id)
		workTrigger.trySend(Unit)
	}
	
	//从当前状态继续
	private fun resumeFromCurrentState() {
		if (_status.value == AgentStatus.PAUSED || _status.value == AgentStatus.WAITING) return
		when (detectNextAction()) {
			NextAction.IDLE -> {
				logger.debug("Next action determined  action=IDLE  agentId={}", agentId)
				updateStatus(AgentStatus.FREE)
			}
			
			NextAction.REQUEST_LLM -> {
				logger.debug("Next action determined  action=REQUEST_LLM  agentId={}", agentId)
				requestLlm()
			}
			
			NextAction.EXECUTE_TOOLS -> {
				logger.debug("Next action determined  action=EXECUTE_TOOLS  agentId={}", agentId)
				executeTools()
			}
		}
	}
	
	//根据AgentContext判断下一步动作
	private fun detectNextAction(): NextAction {
		val round = _context.value.currentRound ?: return NextAction.IDLE
		if (round.pendingToolCalls != null) return NextAction.EXECUTE_TOOLS
		if (round.turns?.lastOrNull()?.tools?.isNotEmpty() == true) return NextAction.REQUEST_LLM
		if (round.turns.isNullOrEmpty()) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	//调用llm
	private fun requestLlm() {
		currentJob = scope.launch {
			logger.debug("LLM request started  agentId={}  model={}", agentId, currentModel.modelInfo.modelId)
			val result = RequestLlmPhase.execute(this@Agent)
			if (result == PhaseResult.Done || result == PhaseResult.Continue) {
				checkAutoCompact()
			}
			when (result) {
				PhaseResult.Continue -> {
					logger.debug("LLM phase continued  agentId={}", agentId)
					workTrigger.trySend(Unit)
				}
				
				PhaseResult.Done -> logger.debug("LLM phase completed  agentId={}", agentId)
				PhaseResult.Error -> logger.warn("Failed to complete LLM phase  agentId={}", agentId)
			}
		}
	}
	
	//启动compact
	private fun launchCompact() {
		if (compactJob?.isActive == true) return
		val rounds = _context.value.historyRounds
		if (rounds.isNullOrEmpty()) return
		logger.debug("Compact launched  agentId={}  roundCount={}", agentId, rounds.size)
		compactJob = scope.launch {
			CompactPhase.execute(this@Agent, rounds, summarizeModel, currentFallbackModels, service)
		}
	}
	
	//compact检查
	private fun checkAutoCompact() {
		if (compactJob?.isActive == true) return
		val rounds = _context.value.historyRounds
		if (rounds.isNullOrEmpty()) return
		val config = currentModel.config ?: return
		val usage = rounds.lastOrNull()?.finalAssistantMessage?.usageSnapshot?.usage ?: return
		val contextWindow = currentModel.modelInfo.contextWindow
		val shouldCompact =
			(config.compactContextUsage != null && usage.totalTokens.toDouble() / contextWindow >= config.compactContextUsage!!) || (config.compactTotalTokens != null && usage.totalTokens >= config.compactTotalTokens!!)
		if (shouldCompact) {
			logger.debug(
				"Auto-compact triggered  agentId={}  usage={}  contextWindow={}",
				agentId,
				usage.totalTokens,
				contextWindow
			)
			launchCompact()
		}
	}
	
	//处理工具调用
	private fun executeTools() {
		currentJob = scope.launch {
			logger.debug("Tool execution phase started  agentId={}", agentId)
			val result = ValidateToolCallsPhase.execute(this@Agent)
			when (result) {
				PhaseResult.Continue -> {
					logger.debug("Tool execution decided  result=Continue  agentId={}", agentId)
					workTrigger.trySend(Unit)
				}
				
				PhaseResult.Done -> logger.debug("Tool execution completed  agentId={}", agentId)
				PhaseResult.Error -> logger.warn("Failed to execute tools  agentId={}", agentId)
			}
		}
	}
	
	//下一步动作
	private enum class NextAction {
		IDLE, REQUEST_LLM, EXECUTE_TOOLS,
	}
	
	//外部发送命令
	fun dispatch(command: AgentCommand) {
		logger.debug("Command dispatched  agentId={}  commandType={}", agentId, command::class.simpleName)
		when (command) {
			is AgentCommand.Directive -> directiveChannel.trySend(command)
			is AgentCommand.Message -> messageChannel.trySend(command)
		}
	}
}
