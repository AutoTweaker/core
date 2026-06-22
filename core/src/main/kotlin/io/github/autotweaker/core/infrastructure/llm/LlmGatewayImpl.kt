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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
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
			"request",
			"request=$request, apiKey=${maskKey(apiKey)}, baseUrl=$baseUrl, providerType=$providerType, timeout=$timeout, chatId=$chatId"
		)
		return LlmClientLoader.load(providerType).chat(request, apiKey, baseUrl, timeout).onEach { result ->
			trace.add("response", "result=$result, chatId=$chatId")
		}
	}
	
	private fun maskKey(key: String): String {
		if (key.length <= 8) return "***"
		return "${key.take(4)}***${key.takeLast(4)}"
	}
}
