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

import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ProviderData
import java.util.*

interface ProviderRepository {
	fun listAvailable(): List<String>
	fun getMeta(type: String): LlmClient.ProviderInfo
	fun list(): List<CoreConfig.ProviderConfig.Provider>
	fun delete(id: UUID)
	fun create(provider: CoreConfig.ProviderConfig.Provider)
	fun updateType(id: UUID, new: String)
	fun updateKey(id: UUID, keyName: String)
	fun updateUrl(id: UUID, url: Url)
	fun updateRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
	fun updateDisplayName(id: UUID, displayName: String)
}
