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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.LlmResult
import io.github.autotweaker.api.types.llm.UsageEntry
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class ChatService(
	private val modelRepo: ModelResolver,
	private val usageRepo: UsageRepository,
	private val chat: ResilientChat,
) : Loggable, Traceable {
	fun chat(request: ChatRequest): Flow<ChatResult> = flow { // 模型解析需要 suspend 上下文，这里借用冷流的
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
		emitAll(
			chat.execute(
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
				val final = chunk.result as? LlmResult.Assembled ?: return@onEach
				final.usage?.let {
					trace.catching {
						usageRepo.save(
							UsageEntry(
								agentId = null,
								modelId = chunk.model,
								timestamp = now(),
								usage = it
							)
						)
					}.rethrowCancellation()
						.onFailure { e ->
							log.error("Failed usage record save", e)
						}
				}
			}
		)
	}
}
