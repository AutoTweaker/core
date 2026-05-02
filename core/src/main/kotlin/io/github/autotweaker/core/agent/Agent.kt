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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.phase.*
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.workspace.Workspace
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

@Suppress("unused")
class Agent(
	override var context: AgentContext,
	override val workspace: Workspace,
	model: Model,
	fallbackModels: List<Model>?,
	thinking: Boolean,
	override val summarizeModel: Model,
	override val containerConfig: ContainerConfig,
	override val settings: List<SettingItem>,
	tools: List<Tool>,
) : AgentEnvironment {
	override val toolCancelledMessage: String = settings.find("core.agent.tool.response.canceled")
	override val toolRejectedMessage: String = settings.find("core.agent.tool.response.rejected")
	override val toolRejectedWithFeedbackMessage: String =
		settings.find("core.agent.tool.response.rejected.with.feedback")
	
	//工具状态
	override val agentState = MutableAgentState()
	
	//上下文锁
	override val contextMutex = Mutex()
	
	override suspend fun updateContext(transform: suspend (AgentContext) -> AgentContext) {
		contextMutex.withLock {
			context = transform(context)
		}
	}
	
	//工具列表
	override val tools = Tools(settings).also { t -> tools.forEach { t.add(it) } }
	
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
	
	//llm输出转agentOutput
	private val streamProcessor = AgentStreamProcessor(
		emitOutput = { _output.emit(it) },
		onStatusChange = { _status.value = it },
		onContextUpdate = { transform -> updateContext(transform) },
	)
	
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
		_status.value = status
	}
	
	//初始化
	init {
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
			for (signal in workTrigger) {
				resumeFromCurrentState()
			}
		}
	}
	
	//处理指令（即时
	private suspend fun handleDirective(directive: AgentCommand.Directive) {
		when (directive) {
			is AgentCommand.Directive.Stop -> {
				//取消协程
				currentJob?.cancel()
				currentJob = null
				compactJob?.cancel()
				compactJob = null
				//归档上下文
				archiveCurrentRound(this, this::updateContext)
				//更新状态
				updateStatus(AgentStatus.FREE)
			}
			
			is AgentCommand.Directive.UpdateModel -> {
				//更新模型
				currentModel = directive.model
				directive.fallbackModels?.let { currentFallbackModels = it }
				directive.thinking?.let { currentThinking = it }
			}
			
			is AgentCommand.Directive.Pause -> {
				if (_status.value == AgentStatus.FREE || _status.value == AgentStatus.ERROR || _status.value == AgentStatus.PAUSED || _status.value == AgentStatus.WAITING) return
				updateStatus(AgentStatus.PAUSED)
			}
			
			is AgentCommand.Directive.Resume -> {
				if (_status.value != AgentStatus.PAUSED) return
				updateStatus(AgentStatus.FREE)
				workTrigger.trySend(Unit)
			}
			
			is AgentCommand.Directive.Cancel -> {
				if (_status.value == AgentStatus.TOOL_CALLING) {
					currentJob?.cancel()
				}
				compactJob?.cancel()
				compactJob = null
			}
			
			is AgentCommand.Directive.Retry -> {
				if (_status.value != AgentStatus.ERROR) return
				updateStatus(AgentStatus.FREE)
				workTrigger.trySend(Unit)
			}
			
			is AgentCommand.Directive.Compact -> launchCompact()
		}
	}
	
	//处理消息
	private suspend fun handleMessage(message: AgentCommand.Message) {
		when (message) {
			is AgentCommand.Message.SendMessage -> {
				//如果非空闲丢弃消息
				if (_status.value != AgentStatus.FREE) return
				//处理消息
				processUserMessage(message.content, message.images)
			}
			
			is AgentCommand.Message.ApproveToolCall -> {
				//回去排队
				if (_status.value != AgentStatus.WAITING) {
					messageChannel.trySend(message)
					return
				}
				//没有待批准的了，丢弃消息
				if (agentState.pendingApproval == null) return
				//处理批准，工具调用时启动协程
				val result = handleApprovalPhase(this@Agent, message.approvals) { result, call ->
					scope.async {
						executeApprovedToolPhase(this@Agent, result, call)
					}.also { currentJob = it }.await()
				}
				when (result) {
					PhaseResult.Continue -> workTrigger.trySend(Unit)
					PhaseResult.Done -> {}
					PhaseResult.Error -> {}
				}
			}
		}
	}
	
	//处理用户消息
	private suspend fun processUserMessage(content: String, images: List<Base64>? = null) {
		//构建Message.User
		val userMsg = AgentContext.Message.User(
			content = content,
			images = images,
			timestamp = Clock.System.now()
		)
		//更新上下文
		updateContext {
			it.copy(currentRound = AgentContext.CurrentRound(userMessage = userMsg, turns = null))
		}
		//开始迭代
		workTrigger.trySend(Unit)
	}
	
	//从当前状态继续
	private fun resumeFromCurrentState() {
		if (_status.value == AgentStatus.PAUSED || _status.value == AgentStatus.WAITING) return
		when (detectNextAction()) {
			NextAction.IDLE -> updateStatus(AgentStatus.FREE)
			NextAction.REQUEST_LLM -> requestLlm()
			NextAction.EXECUTE_TOOLS -> executeTools()
		}
	}
	
	//根据AgentContext判断下一步动作
	private fun detectNextAction(): NextAction {
		val round = context.currentRound ?: return NextAction.IDLE
		if (round.pendingToolCalls != null) return NextAction.EXECUTE_TOOLS
		if (round.turns?.lastOrNull()?.tools?.isNotEmpty() == true) return NextAction.REQUEST_LLM
		if (round.turns.isNullOrEmpty()) return NextAction.REQUEST_LLM
		error("Unknown context state")
	}
	
	//调用llm
	private fun requestLlm() {
		//启动协程
		currentJob = scope.launch {
			//调用对应方法
			val result = requestLlmPhase(this@Agent, streamProcessor)
			//compact检查
			if (result == PhaseResult.Done || result == PhaseResult.Continue) {
				checkAutoCompact()
			}
			//处理结果
			when (result) {
				PhaseResult.Continue -> workTrigger.trySend(Unit)
				PhaseResult.Done -> {}
				PhaseResult.Error -> {}
			}
		}
	}
	
	//启动compact
	private fun launchCompact() {
		if (compactJob?.isActive == true) return
		val rounds = context.historyRounds
		if (rounds.isNullOrEmpty()) return
		compactJob = scope.launch {
			compactPhase(this@Agent, rounds, rounds.size, summarizeModel, currentFallbackModels, settings)
		}
	}
	
	//compact检查
	private fun checkAutoCompact() {
		if (compactJob?.isActive == true) return
		val rounds = context.historyRounds
		if (rounds.isNullOrEmpty()) return
		val config = currentModel.config ?: return
		val usage = rounds.lastOrNull()?.finalAssistantMessage?.usage ?: return
		val contextWindow = currentModel.modelInfo.contextWindow
		val shouldCompact = (config.compactContextUsage != null &&
				usage.totalTokens.toDouble() / contextWindow >= config.compactContextUsage) ||
				(config.compactTotalTokens != null &&
						usage.totalTokens >= config.compactTotalTokens)
		if (shouldCompact) launchCompact()
	}
	
	//处理工具调用
	private fun executeTools() {
		currentJob = scope.launch {
			//调用对应方法
			val result = validateToolCallsPhase(this@Agent)
			when (result) {
				PhaseResult.Continue -> workTrigger.trySend(Unit)
				PhaseResult.Done -> {}
				PhaseResult.Error -> {}
			}
		}
	}
	
	//下一步动作
	private enum class NextAction {
		IDLE,
		REQUEST_LLM,
		EXECUTE_TOOLS,
	}
	
	//外部发送命令
	fun dispatch(command: AgentCommand) {
		when (command) {
			is AgentCommand.Directive -> directiveChannel.trySend(command)
			is AgentCommand.Message -> messageChannel.trySend(command)
		}
	}
}
