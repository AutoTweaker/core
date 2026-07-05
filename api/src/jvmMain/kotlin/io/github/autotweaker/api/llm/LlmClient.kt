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

package io.github.autotweaker.api.llm

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.*
import kotlinx.coroutines.flow.Flow

/**
 * 对接一种 LLM 提供商的协议类型，需要打上 `@AutoService(LlmClient::class)` 来让 AutoTweaker 发现。
 */
interface LlmClient {
	val providerInfo: ProviderInfo
	
	/**
	 * 声明提供商的一些基本信息。
	 */
	data class ProviderInfo(
		/**
		 * 这个字段在 API 层叫做“提供商类型”，名为 `deepseek` 的 [LlmClient] 也可能用于其他与 DeepSeek 的 api 协议兼容的提供商。
		 */
		val name: String,
		/**
		 * 默认的 api 端点，可被用户覆盖。
		 */
		val baseUrl: Url,
		/**
		 * 提供若干此提供商的模型，此时假设用户使用的就是 [baseUrl]。
		 */
		val models: List<ModelData.ModelInfo>,
		/**
		 * 对于不同 HTTP 状态码的错误处理策略。
		 */
		val errorHandlingRules: List<ProviderData.ErrorHandlingRule>
	)
	
	/**
	 * 开始一次 LLM 请求。
	 */
	suspend fun chat(
		request: ChatRequest,
		apiKey: String,
		baseUrl: Url? = null,
		timeout: ChatTimeout? = null
	): Flow<ChatResult>
	
	/**
	 * AutoTweaker 会在程序关闭前调用所有 [LlmClient] 的 [shutdown]。
	 */
	suspend fun shutdown()
}
