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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("unused")
object ContainerManager {
	private val mutex = Mutex()
	
	@Volatile
	private var _service: ContainerService? = null
	
	@Volatile
	private var _containerId: String? = null
	
	val isRunning: Boolean get() = _containerId != null
	val containerId: String? get() = _containerId
	
	suspend fun start(
		service: ContainerService,
		image: String,
		config: ContainerConfig = ContainerConfig(),
	): String = mutex.withLock {
		if (_containerId != null) {
			throw ContainerAlreadyRunningException(_containerId!!)
		}
		_service = service
		val id = service.start(image, config)
		_containerId = id
		id
	}
	
	suspend fun stop() {
		mutex.withLock {
			val id = _containerId ?: return@withLock
			val svc = _service ?: return@withLock
			try {
				svc.stop(id)
			} finally {
				_containerId = null
				_service = null
			}
		}
	}
	
	suspend fun exec(vararg cmd: String): CommandResult {
		val (id, svc) = requireContainer()
		return svc.exec(id, cmd.toList())
	}
	
	suspend fun execShell(command: String): CommandResult {
		val (id, svc) = requireContainer()
		return svc.exec(id, listOf("bash", "-c", command))
	}
	
	private fun requireContainer(): Pair<String, ContainerService> {
		val id = _containerId ?: throw NoContainerRunningException()
		val svc = _service ?: throw NoContainerRunningException()
		return id to svc
	}
}
