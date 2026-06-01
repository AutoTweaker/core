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

import io.github.autotweaker.api.dev.DbAPI
import io.github.autotweaker.api.types.dev.SecretEntry
import java.util.*

object SecretDbApi : DbAPI<SecretEntry> {
	override suspend fun list(range: UIntRange): List<SecretEntry> {
		val all = SecretManager.list()
		val count = (range.last - range.first + 1u).toInt()
		return all.drop(range.first.toInt()).take(count).map { id ->
			SecretEntry(key = id.toString(), content = SecretManager.get(id))
		}
	}
	
	override suspend fun get(key: String): SecretEntry? {
		val id = UUID.fromString(key)
		if (id !in SecretManager.list()) return null
		return SecretEntry(key = key, content = SecretManager.get(id))
	}
	
	override suspend fun put(content: SecretEntry) {
		SecretManager.add(content.content, UUID.fromString(content.key))
	}
	
	override suspend fun delete(key: String) {
		SecretManager.remove(UUID.fromString(key))
	}
}
