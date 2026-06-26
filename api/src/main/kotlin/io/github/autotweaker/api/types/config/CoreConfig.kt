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

package io.github.autotweaker.api.types.config

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import java.util.*

sealed class CoreConfig {
	sealed class JsonConfig {
		data class Env(
			val id: String, val value: String, val type: Type
		) {
			enum class Type {
				BASH_ENV, CONTAINER_ENV
			}
		}
	}
	
	sealed class ProviderConfig {
		data class Provider(
			val id: UUID = UUID.randomUUID(),
			val type: String,
			val keyId: String,
			val baseUrl: Url?,
			val displayName: String,
			val errorHandlingRules: List<ProviderData.ErrorHandlingRule>?
		)
		
		data class Model(
			val data: ModelData,
		)
		
		data class ApiKey(
			val name: String,
			val key: String,
		)
	}
}
