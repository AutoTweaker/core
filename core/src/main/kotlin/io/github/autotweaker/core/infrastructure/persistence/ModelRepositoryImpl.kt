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

package io.github.autotweaker.core.infrastructure.persistence

import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.domain.port.SecretStore
import java.util.*

object ModelRepositoryImpl : ModelRepository {
	private lateinit var secretStore: SecretStore
	
	fun init(secretStore: SecretStore) {
		this.secretStore = secretStore
	}
	
	override fun resolve(id: UUID): Model? {
		val model = ModelStore.get(id) ?: return null
		val provider = ProviderStore.get(model.providerId) ?: return null
		return Model(
			id = model.id,
			provider = Provider(
				id = provider.id,
				name = provider.providerType,
				baseUrl = provider.baseUrl,
				apiKey = secretStore.get(provider.apiKey),
				errorHandlingRules = provider.errorHandlingRules,
			),
			modelInfo = model.modelInfo,
			config = model.config,
		)
	}
	
	override fun resolveAll(ids: List<UUID>): List<Model?> = ids.map { resolve(it) }
}
