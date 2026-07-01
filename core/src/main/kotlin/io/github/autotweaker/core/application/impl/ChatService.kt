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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.session.UsageStore
import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.time.Clock

object ChatService : Loggable {
	private lateinit var modelRepo: ModelResolver
	private lateinit var sessionRepository: SessionRepository
	
	fun init(modelRepo: ModelResolver, sessionRepository: SessionRepository) {
		this.modelRepo = modelRepo
		this.sessionRepository = sessionRepository
	}
	
	fun chat(request: CoreLlmRequest): Flow<CoreLlmResult> = flow {
		val model = modelRepo.resolve(request.model) ?: error("Unknown model: ${request.model}")
		val fallbacks = request.fallbackModels?.map {
			modelRepo.resolve(it) ?: error("Unknown fallback model: $it")
		}
		log.info(
			"Started chat request  model={}  fallbackCount={}  stream={}",
			request.model,
			fallbacks?.size ?: 0,
			request.stream
		)
		val modelMap = buildMap {
			put(model.id, model)
			fallbacks?.forEach { put(it.id, it) }
		}
		var lastUsage: Usage? = null
		var lastModelId: UUID? = null
		emitAll(
			ResilientChat.execute(
				model = model,
				fallbackModels = fallbacks,
				messages = request.messages,
				tools = request.tools,
				responseFormat = request.responseFormat,
				stream = request.stream,
				thinking = request.thinking,
				timeout = request.timeout
			).onEach { result ->
				result.result.usage?.let { lastUsage = it }
				lastModelId = result.model
			}.onCompletion { cause ->
				if (cause == null) {
					lastUsage?.let { usage ->
						val resolvedModel = lastModelId?.let { modelMap[it] } ?: return@let
						val record = SessionMessage.UsageRecord(
							UUID.randomUUID(), Clock.System.now(), UsageSnapshot(usage, resolvedModel.modelInfo)
						)
						sessionRepository.saveMessages(listOf(record))
						UsageStore.collect(listOf(record))
					}
				}
			})
	}
}
