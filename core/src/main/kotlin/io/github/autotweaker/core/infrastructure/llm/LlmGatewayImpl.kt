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

package io.github.autotweaker.core.infrastructure.llm

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.UpperSnakeCase.Companion.toUpperSnake
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.ChatTimeout
import io.github.autotweaker.core.domain.port.LlmGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.*

object LlmGatewayImpl : LlmGateway, Loggable, Traceable {
	override suspend fun send(
		request: ChatRequest,
		apiKey: String,
		baseUrl: Url,
		providerType: String,
		timeout: ChatTimeout,
	): Flow<ChatResult> {
		val chatId = UUID.randomUUID()
		log.debug(
			"Sent LLM request  providerType={}  model={}  stream={}  chatId={}",
			providerType,
			request.model,
			request.stream, chatId
		)
		trace.add(
			"request".toKebab(),
			mapOf(
				"CHAT_ID".toUpperSnake() to chatId,
				"REQUEST".toUpperSnake() to request,
				"API_KEY".toUpperSnake() to apiKey.toMasked(),
				"BASE_URL".toUpperSnake() to baseUrl,
				"PROVIDER_TYPE".toUpperSnake() to providerType,
				"TIMEOUT".toUpperSnake() to timeout,
			)
		
		)
		return LlmClientLoader.load(providerType)
			.chat(request, apiKey, baseUrl, timeout)
			.onEach { result ->
				trace.add(
					"response".toKebab(), mapOf(
						"CHAT_ID".toUpperSnake() to chatId,
						"RESULT".toUpperSnake() to result
					)
				)
			}
	}
}
