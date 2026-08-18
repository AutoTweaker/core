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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.serializer
import java.util.*

abstract class EnvStore : MutableStore<MutableMap<String, UUID>>(), Loggable, Traceable {
	override val serializer = MutableMapSerializer(String.serializer(), UuidSerializer)
	override fun default() = mutableMapOf<String, UUID>()
	
	suspend fun listEnv(): List<String> = transform {
		it.keys.toList()
	}
	
	suspend fun getEnv(id: String): String? = transform {
		val uuid = it[id] ?: return@transform null
		trace.catching { secretStore.get(uuid) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed secret retrieval  name={}  reason={}", id, e.message)
			}.getOrNull()
	}
	
	suspend fun setEnv(id: String, value: String) = transform {
		val uuid = UUID()
		trace.catching { secretStore.set(value, uuid) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed secret set  name={}  reason={}", id, e.message)
			}.getOrThrow()
		it.put(id, uuid).also { old ->
			old ?: return@also
			secretStore.remove(old)
		}.discard()
	}.andLog(log) {
		info("Set env  name={}", id)
	}
	
	suspend fun removeEnv(id: String): Boolean = transform {
		val uuid = it[id] ?: return@transform false
		trace.catching { secretStore.remove(uuid) }
			.rethrowCancellation()
			.rethrow<SecretStoreLockedException>()
			.onFailure { e ->
				log.warn("Failed to remove secret  name={}  reason={}", id, e.message)
			}
		it.remove(id)
		return@transform true
	}.andLog(log) {
		if (it) info("Removed env  name={}", id)
	}
	
	companion object {
		private lateinit var secretStore: SecretStore
		
		fun init(secretStore: SecretStore) {
			this.secretStore = secretStore
		}
	}
}
