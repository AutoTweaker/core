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

package io.github.autotweaker.core.session

import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.data.ModelStore
import io.github.autotweaker.core.data.ProviderStore
import io.github.autotweaker.core.llm.LlmClientLoader
import io.github.autotweaker.core.secret.impl.SecretManager
import java.util.*

object ProviderService {
	fun getInfo(type: String): LlmClient.ProviderInfo = LlmClientLoader.load(type).providerInfo
	
	fun getModel(id: UUID): Model? {
		val model = ModelStore.get(id) ?: return null
		val provider = ProviderStore.get(model.providerId) ?: return null
		val providerData = Provider(
			name = provider.providerType,
			baseUrl = provider.baseUrl,
			apiKey = SecretManager.get(provider.apiKey),
			errorHandlingRules = provider.errorHandlingRules
		)
		return Model(
			id = model.id,
			provider = providerData,
			modelInfo = model.modelInfo,
			config = model.config,
		)
	}
}
