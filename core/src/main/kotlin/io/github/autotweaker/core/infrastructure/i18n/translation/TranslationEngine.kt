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

package io.github.autotweaker.core.infrastructure.i18n.translation

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.core.application.impl.ChatService
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.infrastructure.i18n.I18nServiceImpl
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.time.Clock

object TranslationEngine : Loggable, Traceable, Settable {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
	
	data class BatchJob(
		val model: Model,
		val systemPrompt: String,
		val userPromptTemplate: String,
		val target: Locale,
		val batch: Map<String, List<String>>,
	)
	
	private data class BatchResult(
		val translated: Map<String, String>,
		val batch: Map<String, List<String>>,
		val target: Locale,
	)
	
	suspend fun run(
		modelId: UUID, target: Locale,
		modelRepo: ModelResolver,
	) {
		val model = modelRepo.resolve(modelId) ?: error("Model not found: $modelId")
		val systemPrompt = setting(TranslateSettings.SystemPrompt())
			.replace("{{target_language}}", target.displayName)
		val userPromptTemplate = setting(TranslateSettings.UserPrompt())
		val batchSize = setting(TranslateSettings.BatchSize())
		val limit = setting(TranslateSettings.MaxConcurrent())
		
		val baths = collectBaths(target).ifEmpty { return }
		val jobs = baths.toList().chunked(batchSize) {
			BatchJob(model, systemPrompt, userPromptTemplate, target, it.toMap())
		}
		
		jobs.forEachParallel(limit) { job ->
			translateBatch(job)?.let { persistResults(it) } ?: log.warn(
				"Failed translation  keys={}  firstKey={}",
				job.batch.keys.size, job.batch.keys.firstOrNull()
			)
		}
		
		log.info("Completed translation  target={}  keys={}", target.toLanguageTag(), baths.size)
	}
	
	private fun collectBaths(target: Locale): Map<String, List<String>> {
		val result = mutableMapOf<String, List<String>>()
		val all = I18nServiceImpl.getAllEntries()
		all.forEach { (key, localizations) ->
			if (localizations[target] == null) result[key] = localizations.values.toList()
		}
		return result
	}
	
	fun isCompleted(target: Locale): Boolean =
		I18nServiceImpl.getAllEntries().all {
			it.value[target] != null
		}
	
	
	private suspend fun translateBatch(job: BatchJob): BatchResult? {
		val contentJson = json.encodeToString(job.batch)
		val userPrompt = job.userPromptTemplate.replace(
			"{{target_language}}", job.target.displayName
		).replace("{{content_to_translate}}", contentJson)
		
		val request = CoreLlmRequest(
			model = job.model.id,
			fallbackModels = null,
			messages = listOf(
				ChatMessage.SystemMessage(job.systemPrompt, Clock.System.now()),
				ChatMessage.UserMessage(userPrompt, Clock.System.now()),
			),
			stream = false,
			thinking = setting(TranslateSettings.Thinking()),
			responseFormat = ChatRequest.ResponseFormat(ChatRequest.ResponseFormat.Type.JSON_OBJECT)
		)
		val results = trace.catching { ChatService.chat(request).toList() }
			.rethrowCancellation().getOrElse { return null }
		
		val finalResult = results.asSequence().filterNot {
			it.result.message is ChatMessage.ErrorMessage
		}.map { it.result }.lastOrNull() as? ChatResult.Assembled ?: return null
		
		val text = (finalResult.message as ChatMessage.AssistantMessage).content ?: return null
		return parseResponse(text)?.let { BatchResult(it, job.batch, job.target) }
	}
	
	private suspend fun persistResults(result: BatchResult) {
		result.translated.forEachParallel { (key, value) ->
			val sourceText = result.batch[key]?.firstOrNull() ?: return@forEachParallel
			if (PlaceholderValidator.validate(sourceText, value))
				trace.catching { I18nServiceImpl.set(key, value, result.target) }
					.rethrowCancellation()
					.onFailure { log.warn("Failed translation persistence  key={}  reason={}", key, it.message) }
			else log.warn(
				"Placeholder validation failed  key={}  source={}  translated={}", key, sourceText, value
			)
		}
	}
	
	
	private fun parseResponse(responseText: String): Map<String, String>? {
		val jsonText = responseText
			.substringAfter('{', missingDelimiterValue = "")
			.substringBeforeLast('}', missingDelimiterValue = "").ifEmpty { return null }
		
		return trace.catching {
			json.decodeFromString<Map<String, String>>(jsonText).orNull()
		}.onFailure { log.warn("Failed translation response parsing  length={}", responseText.length) }
			.getOrNull()
	}
}
