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
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.ModelRepository
import kotlinx.coroutines.flow.Flow

object ChatService {
	private lateinit var modelRepo: ModelRepository
	private lateinit var resilientChat: ResilientChat
	
	fun init(modelRepo: ModelRepository, resilientChat: ResilientChat) {
		this.modelRepo = modelRepo
		this.resilientChat = resilientChat
	}
	
	fun chat(request: CoreLlmRequest): Flow<CoreLlmResult> {
		val model = modelRepo.resolve(request.model) ?: error("Unknown model: ${request.model}")
		val fallbacks = request.fallbackModels?.map {
			modelRepo.resolve(it) ?: error("Unknown fallback model: $it")
		}
		return resilientChat.execute(
			model = model,
			fallbackModels = fallbacks,
			messages = request.messages,
			tools = request.tools,
			responseFormat = request.responseFormat,
			stream = request.stream,
			thinking = request.thinking,
		)
	}
}
