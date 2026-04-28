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
