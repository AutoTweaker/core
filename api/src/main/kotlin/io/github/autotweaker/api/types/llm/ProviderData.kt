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

package io.github.autotweaker.api.types.llm

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ProviderData(
	@Serializable(with = UuidSerializer::class) val id: UUID,
	val displayName: String,
	val providerType: String,
	@Serializable(with = UuidSerializer::class) val apiKey: UUID,
	val baseUrl: Url,
	val models: List<ModelData>,
	val errorHandlingRules: List<ErrorHandlingRule>
) {
	@Serializable
	data class ErrorHandlingRule(
		val statusCode: Int, val strategy: RecoveryStrategy
	) {
		@Serializable
		enum class RecoveryStrategy {
			RETRY, FALLBACK, CONTEXT_FALLBACK, PROVIDER_FALLBACK,
		}
	}
}