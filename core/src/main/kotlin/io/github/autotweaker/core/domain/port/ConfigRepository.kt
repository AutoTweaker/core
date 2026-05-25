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

package io.github.autotweaker.core.domain.port

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import java.util.*

interface ConfigRepository {
	fun listProviders(): List<ProviderData>
	fun addProvider(provider: CoreConfig.ProviderConfig.Provider)
	fun removeProvider(id: UUID)
	fun updateProviderType(id: UUID, type: String)
	fun updateProviderKey(id: UUID, keyName: String)
	fun updateProviderUrl(id: UUID, url: Url)
	fun updateProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
	fun updateProviderDisplayName(id: UUID, displayName: String)
	
	fun listModels(): List<ModelData>
	fun addModel(model: CoreConfig.ProviderConfig.Model)
	fun removeModel(id: UUID)
	fun updateModel(id: UUID, model: CoreConfig.ProviderConfig.Model)
	
	fun listApiKeyNames(): List<String>
	fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
	fun removeApiKey(name: String)
	
	fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
	fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
	fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
	fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)
}
