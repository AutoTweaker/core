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

package io.github.autotweaker.core.infrastructure.data

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.debug.DbAPI
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.debug.SecretEntry
import io.github.autotweaker.api.types.exception.notfound.*
import io.github.autotweaker.core.domain.port.SecretStore
import java.util.*

object SecretDbApi : DbAPI<SecretEntry>, Traceable {
	private lateinit var secretStore: SecretStore
	
	fun init(secretStore: SecretStore) {
		this.secretStore = secretStore
	}
	
	override suspend fun list(range: UIntRange): List<SecretEntry> {
		val all = secretStore.list()
		val count = (range.last - range.first + 1u).toInt()
		return all.drop(range.first.toInt()).take(count).map { id ->
			SecretEntry(key = id.toString(), content = secretStore.get(id))
		}
	}
	
	override suspend fun get(key: String): SecretEntry? {
		val id = UUID.fromString(key)
		return trace.catching { SecretEntry(key = key, content = secretStore.get(id)) }
			.rethrowNot<SecretNotFoundException>()
			.getOrNull()
	}
	
	override suspend fun put(content: SecretEntry) {
		secretStore.set(content.content, UUID.fromString(content.key))
	}
	
	override suspend fun delete(key: String) {
		secretStore.remove(UUID.fromString(key))
	}
}
