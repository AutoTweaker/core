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

package io.github.autotweaker.core.container

import io.github.autotweaker.core.container.docker.DockerJavaService
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.secret.SecretManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.*


object ContainerManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val mutex = Mutex()
	private val jsonEntry = JsonStore.namespace(this::class.java.name)
	
	private val service: ContainerService = DockerJavaService()
	
	@Volatile
	private var _containerId: String? = null
	
	val isRunning: Boolean get() = _containerId != null
	
	@Suppress("unused")
	val containerId: String? get() = _containerId
	
	suspend fun start(): String = mutex.withLock {
		if (_containerId != null) {
			logger.warn("Container already started  containerId={}", _containerId)
			throw ContainerAlreadyRunningException(_containerId!!)
		}
		val config = ContainerConfig(env = getEnv())
		val image = Settings.get().find<String>("core.container.docker.image")
		logger.debug("Container start initiated  image={}", image)
		val id = service.start(image, config)
		_containerId = id
		logger.info("Container started  containerId={}", id)
		id
	}
	
	suspend fun stop() {
		mutex.withLock {
			val id = _containerId ?: return@withLock
			val svc = service
			try {
				logger.debug("Container stop initiated  containerId={}", id)
				svc.stop(id)
			} finally {
				_containerId = null
				service.shutdown()
				logger.info("Container stopped  containerId={}", id)
			}
		}
	}
	
	@Suppress("unused")
	suspend fun exec(vararg cmd: String, env: Map<String, String> = emptyMap()): CommandResult {
		val (id, svc) = requireContainer()
		logger.debug("Container command started  containerId={}  cmd={}", id, cmd.joinToString(" "))
		return svc.exec(id, cmd.toList(), env = env)
	}
	
	suspend fun execShell(command: String, env: Map<String, String> = emptyMap()): CommandResult {
		val (id, svc) = requireContainer()
		logger.debug("Container shell started  containerId={}  command={}", id, command)
		return svc.exec(id, listOf("bash", "-lc", command), env = env)
	}
	
	private fun requireContainer(): Pair<String, ContainerService> {
		val id = _containerId ?: throw NoContainerRunningException()
		val svc = service
		return id to svc
	}
	
	fun listEnv(): List<String> = getEnvUuidMap().keys.toList()
	
	fun setEnv(env: Map<String, String>) {
		logger.debug("Container env set started  count={}", env.size)
		val current = getEnvUuidMap()
		val removed = current.keys - env.keys
		removed.forEach { current[it]?.let { uuid -> SecretManager.remove(uuid) } }
		val updated = current.filterKeys { it in env.keys }.toMutableMap()
		for ((id, value) in env) {
			current[id]?.let { SecretManager.remove(it) }
			updated[id] = SecretManager.add(value)
		}
		saveEnvUuidMap(updated)
		logger.debug("Container env set  count={}", updated.size)
	}
	
	fun removeEnv(id: String) {
		val current = getEnvUuidMap()
		current[id]?.let { SecretManager.remove(it) }
		val updated = current.filterKeys { it != id }.toMutableMap()
		saveEnvUuidMap(updated)
		logger.debug("Container env removed  key={}", id)
	}
	
	fun getEnv(id: String? = null): Map<String, String> {
		val uuidMap = getEnvUuidMap()
		val ids = if (id != null) listOf(id).filter { it in uuidMap } else uuidMap.keys
		return ids.mapNotNull { key ->
			try {
				key to SecretManager.get(uuidMap[key]!!)
			} catch (_: Exception) {
				logger.warn("Failed to get env value  key={}", key)
				null
			}
		}.toMap()
	}
	
	private fun getEnvUuidMap(): Map<String, UUID> {
		val obj = jsonEntry.get() as? JsonObject ?: return emptyMap()
		return obj.mapNotNull { (k, v) ->
			v.jsonPrimitive.contentOrNull?.let { UUID.fromString(it) }?.let { k to it }
		}.toMap()
	}
	
	private fun saveEnvUuidMap(map: Map<String, UUID>) {
		val obj = JsonObject(map.mapValues { (_, v) -> JsonPrimitive(v.toString()) })
		jsonEntry.set(obj)
	}
}
