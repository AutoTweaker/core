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

package io.github.autotweaker.core.infrastructure.persist.json.base

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.serializer
import java.util.*

abstract class SecretMapStore : MutexStore<MutableMap<String, UUID>>(), Loggable, Traceable {
	override val serializer = MutableMapSerializer(String.serializer(), UuidSerializer)
	override fun default() = mutableMapOf<String, UUID>()
	
	protected suspend fun putSecret(name: String, value: String) = transform {
		it[name]?.let { uuid -> secretStore.remove(uuid) }
		it[name] = secretStore.set(value)
	}
	
	protected suspend fun getSecret(name: String): String? = transform {
		val uuid = it[name] ?: return@transform null
		trace.catching { secretStore.get(uuid) }
			.rethrow<SecretStoreLockedException>()
			.onFailure { log.warn("Failed secret retrieval  name={}", name, it) }
			.getOrNull()
	}
	
	protected suspend fun removeSecret(name: String): Boolean = transform {
		val uuid = it.remove(name) ?: return@transform false
		trace.catching { secretStore.remove(uuid) }
			.rethrow<SecretStoreLockedException>()
		return@transform true
	}
	
	protected suspend fun listSecrets(): List<String> = transform {
		it.keys.toList()
	}
	
	companion object {
		private lateinit var secretStore: SecretStore
		
		fun init(secretStore: SecretStore) {
			this.secretStore = secretStore
		}
	}
}
