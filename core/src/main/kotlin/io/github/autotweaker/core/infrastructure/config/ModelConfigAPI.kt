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

package io.github.autotweaker.core.infrastructure.config

import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.infrastructure.persistence.ModelStore
import java.util.*

object ModelConfigAPI : ModelConfigRepository {
	private val store = ModelStore
	
	override fun add(model: CoreConfig.ProviderConfig.Model) {
		store.add(model.data)
	}
	
	override fun list() = store.get().map { CoreConfig.ProviderConfig.Model(data = it) }
	
	override fun remove(id: UUID) = store.delete(id)
	
	override fun update(id: UUID, model: CoreConfig.ProviderConfig.Model) {
		store.override(model.data.copy(id = id))
	}
}
