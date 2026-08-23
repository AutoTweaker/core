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

package io.github.autotweaker.core.application.impl

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.LlmRequest
import io.github.autotweaker.api.types.llm.LlmResult
import io.github.autotweaker.api.types.llm.UsageEntry
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.flow.*

object ChatService : Loggable, Traceable {
	private lateinit var modelRepo: ModelResolver
	private lateinit var sessionRepo: SessionRepository
	private lateinit var usageRepo: UsageRepository
	
	fun init(model: ModelResolver, session: SessionRepository) {
		modelRepo = model
		sessionRepo = session
	}
	
	fun chat(request: LlmRequest): Flow<LlmResult> = flow {
		val model = modelRepo.resolve(request.model)
		val fallbacks = request.fallbackModels?.map {
			modelRepo.resolve(it)
		}
		log.info(
			"Started chat request  model={}  fallbackCount={}  stream={}",
			request.model,
			fallbacks?.size ?: 0,
			request.stream
		)
		var lastUsage: UsageEntry? = null
		emitAll(
			ResilientChat.execute(
				model = model,
				fallbackModels = fallbacks,
				timeout = request.timeout,
				
				instructions = request.instructions,
				messages = request.messages,
				reasoning = request.reasoning,
				stream = request.stream,
				maxTokens = request.maxTokens,
				tools = request.tools,
				temperature = request.temperature,
				jsonOutput = request.jsonOutput
			).onEach { chunk ->
				val result = chunk.result as? ChatResult.Assembled ?: return@onEach
				result.usage?.let {
					lastUsage = UsageEntry(
						modelId = chunk.model,
						timestamp = result.message.timestamp,
						usage = it
					)
				}
			}.onCompletion { cause ->
				if (cause == null)
					lastUsage?.let {
						trace.catching {
							usageRepo.save(listOf(it))
						}.rethrowCancellation()
							.onFailure { e ->
								log.error("Failed usage record save  recordId={}", it.id, e)
							}
					}
			})
	}
}
