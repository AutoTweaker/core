package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.resilientChat
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.Usage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock

private const val MAX_COMPACT_RETRIES = 5
private const val MIN_SUMMARY_LENGTH = 10

internal suspend fun compactPhase(
	env: AgentEnvironment,
	rounds: List<AgentContext.CompletedRound>,
	snapshotSize: Int,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
	settings: List<SettingItem>,
) {
	val compactPrompt: String = settings.find("core.agent.compact.prompt")
	val maxMessageChars: Int = settings.find("core.agent.compact.max.message.chars")
	val messageSummarizePrompt: String = settings.find("core.agent.compact.message.summarize.prompt")
	
	val processed = preprocessMessages(rounds, summarizeModel, fallbackModels, maxMessageChars, messageSummarizePrompt)
	
	val systemAndMessages = processed + ChatMessage.UserMessage(compactPrompt, Clock.System.now())
	
	var attempt = 0
	var finalResult: CompactRequestResult
	while (true) {
		finalResult = runCompactRequest(env, summarizeModel, fallbackModels, systemAndMessages)
		attempt++
		
		if (finalResult.success || attempt >= MAX_COMPACT_RETRIES) break
	}
	
	val cleaned = finalResult.rawContent
	if (cleaned.isBlank()) {
		env.emitOutput(
			AgentOutput.Error(
				"Compact produced empty summary after $attempt attempts",
				AgentOutput.Error.Type.COMPACT
			)
		)
		return
	}
	
	env.updateContext { ctx ->
		ctx.copy(
			historyRounds = ctx.historyRounds?.drop(snapshotSize)?.ifEmpty { null },
			summarizedMessage = cleaned,
		)
	}
	
	env.emitOutput(AgentOutput.ContextUpdate(env.context, AgentOutput.ContextUpdate.UpdateReason.COMPACTED))
}

private data class CompactRequestResult(
	val rawContent: String,
	val usage: Usage?,
	val success: Boolean,
)

private suspend fun runCompactRequest(
	env: AgentEnvironment,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
	messages: List<ChatMessage>,
): CompactRequestResult {
	val request = ChatRequest(
		model = summarizeModel.name,
		messages = messages,
		stream = true,
		thinking = false,
	)
	
	var rawContent = ""
	var lastUsage: Usage? = null
	var hasError = false
	try {
		val results = resilientChat(summarizeModel, fallbackModels, request)
		results.collect { resilientResult ->
			currentCoroutineContext().ensureActive()
			val result = resilientResult.result
			val msg = result.message
			
			if (msg is ChatMessage.ErrorMessage) {
				env.emitOutput(AgentOutput.CompactOutput(AgentOutput.CompactOutput.Status.FAILED, rawContent, null))
				hasError = true
				return@collect
			}
			
			val assistantMsg = msg as? ChatMessage.AssistantMessage ?: return@collect
			
			if (!assistantMsg.content.isNullOrEmpty()) {
				rawContent += assistantMsg.content
				env.emitOutput(AgentOutput.CompactOutput(AgentOutput.CompactOutput.Status.OUTPUTTING, rawContent, null))
			}
			
			result.usage?.let { lastUsage = it }
		}
	} catch (e: CancellationException) {
		throw e
	} catch (_: Exception) {
		env.emitOutput(AgentOutput.CompactOutput(AgentOutput.CompactOutput.Status.FAILED, rawContent, null))
		return CompactRequestResult(rawContent, lastUsage, success = false)
	}
	
	if (hasError) return CompactRequestResult(rawContent, lastUsage, success = false)
	
	val extracted = extractSummary(rawContent)
	val valid = extracted.length >= MIN_SUMMARY_LENGTH
	
	if (valid) {
		env.emitOutput(AgentOutput.CompactOutput(AgentOutput.CompactOutput.Status.FINISHED, rawContent, lastUsage))
	} else {
		env.emitOutput(AgentOutput.CompactOutput(AgentOutput.CompactOutput.Status.FAILED, rawContent, lastUsage))
	}
	
	return CompactRequestResult(extracted, lastUsage, success = valid)
}

private suspend fun preprocessMessages(
	rounds: List<AgentContext.CompletedRound>,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
	maxMessageChars: Int,
	messageSummarizePrompt: String,
): List<ChatMessage> = buildList {
	for (round in rounds) {
		add(
			convertUserMessage(
				round.userMessage,
				maxMessageChars,
				messageSummarizePrompt,
				summarizeModel,
				fallbackModels
			)
		)
		round.turns?.forEach { turn ->
			val toolCalls = turn.tools.map { tool ->
				ChatMessage.AssistantMessage.ToolCall(
					id = tool.callId,
					name = tool.name,
					arguments = tool.call.arguments,
				)
			}
			add(
				convertAssistantMessage(
					turn.assistantMessage,
					toolCalls,
					maxMessageChars,
					messageSummarizePrompt,
					summarizeModel,
					fallbackModels
				)
			)
			turn.tools.forEach {
				add(
					convertToolMessage(
						it,
						maxMessageChars,
						messageSummarizePrompt,
						summarizeModel,
						fallbackModels
					)
				)
			}
		}
		round.finalAssistantMessage?.let {
			add(
				convertAssistantMessage(
					it,
					null,
					maxMessageChars,
					messageSummarizePrompt,
					summarizeModel,
					fallbackModels
				)
			)
		}
	}
}

private suspend fun convertUserMessage(
	msg: AgentContext.Message.User,
	maxMessageChars: Int,
	messageSummarizePrompt: String,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
): ChatMessage.UserMessage {
	val content = buildString {
		if (!msg.images.isNullOrEmpty()) {
			repeat(msg.images.size) { appendLine("<image_placeholder/>") }
		}
		append(msg.content ?: "")
	}
	val finalContent = if (content.length > maxMessageChars) {
		summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels)
	} else content
	return ChatMessage.UserMessage(finalContent, msg.timestamp)
}

private suspend fun convertAssistantMessage(
	msg: AgentContext.Message.Assistant,
	toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
	maxMessageChars: Int,
	messageSummarizePrompt: String,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
): ChatMessage.AssistantMessage {
	val content = msg.content ?: ""
	val finalContent = if (content.length > maxMessageChars) {
		summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels)
	} else content
	return ChatMessage.AssistantMessage(
		content = finalContent,
		createdAt = msg.timestamp,
		reasoningContent = msg.reasoning ?: "",
		toolCalls = toolCalls,
		model = msg.model.name,
	)
}

private suspend fun convertToolMessage(
	msg: AgentContext.Message.Tool,
	maxMessageChars: Int,
	messageSummarizePrompt: String,
	summarizeModel: Model,
	fallbackModels: List<Model>?,
): ChatMessage.ToolMessage {
	val content = msg.result.content
	val finalContent = if (content.length > maxMessageChars) {
		summarizeMessage(content, messageSummarizePrompt, summarizeModel, fallbackModels)
	} else content
	return ChatMessage.ToolMessage(
		content = finalContent,
		createdAt = msg.result.timestamp,
		toolCallId = msg.callId,
	)
}

private suspend fun summarizeMessage(
	content: String,
	prompt: String,
	model: Model,
	fallbackModels: List<Model>?,
): String {
	val request = ChatRequest(
		model = model.name,
		thinking = false,
		messages = listOf(
			ChatMessage.UserMessage(prompt.format(content), Clock.System.now()),
		),
		stream = false,
	)
	val results = resilientChat(model, fallbackModels, request).toList()
	val success = results.filter { it.retrying == null }.map { it.result }
	return success.firstNotNullOfOrNull { it.message?.content } ?: content
}

private fun extractSummary(text: String): String {
	val match = Regex("<summary>([\\s\\S]*?)</summary>").find(text)
	return match?.groupValues?.get(1)?.trim() ?: text
}
