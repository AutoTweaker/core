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

import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.secret.impl.SecretManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

class EnvStorage(kClass: KClass<*>) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(kClass)
	
	fun listEnv(): List<String> = getEnvUuidMap().keys.toList()
	
	fun getEnv(id: String): String? {
		val uuid = getEnvUuidMap()[id] ?: return null
		return try {
			SecretManager.get(uuid)
		} catch (_: Exception) {
			null
		}
	}
	
	fun setEnv(id: String, value: String) {
		val current = getEnvUuidMap()
		current[id]?.let { SecretManager.remove(it) }
		val uuid = SecretManager.add(value)
		val updated =
			JsonObject(current.mapValues { (_, v) -> JsonPrimitive(v.toString()) } + (id to JsonPrimitive(uuid.toString())))
		jsonEntry.set(updated)
		logger.debug("Env set  id={}", id)
	}

	fun removeEnv(id: String) {
		val current = getEnvUuidMap()
		current[id]?.let { SecretManager.remove(it) }
		val updated = JsonObject(current.filterKeys { it != id }.mapValues { (_, v) -> JsonPrimitive(v.toString()) })
		jsonEntry.set(updated)
		logger.debug("Env removed  id={}", id)
	}
	
	private fun getEnvUuidMap(): Map<String, UUID> {
		val obj = jsonEntry.get() as? JsonObject ?: return emptyMap()
		return obj.mapNotNull { (k, v) ->
			v.jsonPrimitive.contentOrNull?.let { UUID.fromString(it) }?.let { k to it }
		}.toMap()
	}
}