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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.serializer
import java.util.*

abstract class SecretMapStore : MutableStore<MutableMap<String, UUID>>(), Loggable, Traceable {
	override val serializer = MutableMapSerializer(String.serializer(), UuidSerializer)
	override fun default() = mutableMapOf<String, UUID>()
	
	protected suspend fun putSecret(name: String, value: String) = transform {
		val id = trace.catching { secretStore.set(value) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed secret set  name={}  reason={}", name, e.message)
			}.getOrThrow()
		it.put(name, id).also { old ->
			old ?: return@also
			secretStore.remove(old)
		}.discard()
	}
	
	protected suspend fun getSecret(name: String): String? = transform {
		val uuid = it[name] ?: return@transform null
		trace.catching { secretStore.get(uuid) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed secret retrieval  name={}  reason={}", name, e.message)
			}.getOrNull()
	}
	
	protected suspend fun removeSecret(name: String): Boolean = transform {
		val uuid = it[name] ?: return@transform false
		trace.catching { secretStore.remove(uuid) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed to remove secret  name={}  reason={}", name, e.message)
			}
		it.remove(name)
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
