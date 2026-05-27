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

package io.github.autotweaker.core.application.chat

import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.session.UsageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.util.*
import kotlin.time.Clock

object ChatService {
	private lateinit var modelRepo: ModelRepository
	private lateinit var resilientChat: ResilientChat
	private lateinit var sessionRepository: SessionRepository
	
	fun init(modelRepo: ModelRepository, resilientChat: ResilientChat, sessionRepository: SessionRepository) {
		this.modelRepo = modelRepo
		this.resilientChat = resilientChat
		this.sessionRepository = sessionRepository
	}
	
	fun chat(request: CoreLlmRequest): Flow<CoreLlmResult> {
		val model = modelRepo.resolve(request.model) ?: error("Unknown model: ${request.model}")
		val fallbacks = request.fallbackModels?.map {
			modelRepo.resolve(it) ?: error("Unknown fallback model: $it")
		}
		val modelMap = buildMap {
			put(model.id, model)
			fallbacks?.forEach { put(it.id, it) }
		}
		var lastUsage: Usage? = null
		var lastModelId: UUID? = null
		return resilientChat.execute(
			model = model,
			fallbackModels = fallbacks,
			messages = request.messages,
			tools = request.tools,
			responseFormat = request.responseFormat,
			stream = request.stream,
			thinking = request.thinking,
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
		}
	}
}
