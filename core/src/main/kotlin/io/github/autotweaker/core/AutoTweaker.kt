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

package io.github.autotweaker.core

import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.dev.StartupHook
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.application.Launcher
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

object AutoTweaker : CoreAPI.AdapterAPI {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	val version: SemVer by lazy {
		val props = Properties()
		this::class.java.getResourceAsStream("/version.properties")?.use { props.load(it) }
		SemVer.parse(props.getProperty("version"))
	}
	
	private val registry: MutableMap<String, Pair<Adapter, AdapterInfo>> = mutableMapOf()
	private val adapterMutex = Mutex()
	
	private val lockFile: Path = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "autotweaker.lock"
	)
	private var lockChannel: FileChannel? = null
	private var fileLock: FileLock? = null
	
	suspend fun start() {
		withContext(Dispatchers.IO) {
			Files.createDirectories(Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins"))
		}
		acquireLock()
		
		PluginLoader.load<StartupHook>().forEach { hook ->
			logger.info("Executed startup hook  class={}", hook::class.java.name)
			hook.execute(version)
		}
		
		logger.info("Started AutoTweaker  version={}", version)
		
		Launcher.start(version, registry, this)
		Runtime.getRuntime().addShutdownHook(Thread {
			logger.info("Initiated AutoTweaker shutdown")
			runBlocking { Launcher.shutdown(registry.values.toList()) }
			PluginLoader.closeClassLoaders()
			releaseLock()
			logger.info("Completed AutoTweaker shutdown")
		})
	}
	
	private fun acquireLock() {
		Files.createDirectories(lockFile.parent)
		val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
		lockChannel = channel
		val lock = channel.tryLock()
		if (lock == null) {
			channel.close()
			throw IllegalStateException("Another instance is already running")
		}
		fileLock = lock
		channel.truncate(0)
		channel.write(java.nio.ByteBuffer.wrap(ProcessHandle.current().pid().toString().toByteArray()))
		channel.force(true)
		logger.debug("Acquired lock  pid={}  lockFile={}", ProcessHandle.current().pid(), lockFile)
	}
	
	override suspend fun list(): List<AdapterInfo> = registry.values.map { it.second }
	
	override suspend fun start(name: String) = adapterMutex.withLock {
		val (adapter, info) = requireAdapter(name)
		if (adapter.isRunning) error("Adapter already running: ${info.name}")
		adapter.start(Launcher.createCoreAPI(this))
		logger.info("Started adapter  name={}", info.name)
	}
	
	override suspend fun alive(name: String): Boolean {
		val (adapter, _) = requireAdapter(name)
		return adapter.isRunning
	}
	
	override suspend fun stop(name: String) = adapterMutex.withLock {
		val (adapter, info) = requireAdapter(name)
		adapter.stop()
		logger.info("Stopped adapter  name={}", info.name)
	}
	
	private fun requireAdapter(name: String): Pair<Adapter, AdapterInfo> =
		registry[name] ?: error("Unknown adapter: $name")
	
	private fun releaseLock() {
		trace.catching {
			fileLock?.release()
			lockChannel?.close()
			Files.deleteIfExists(lockFile)
		}.onFailure { logger.warn("Failed lock release  lockFile={}  reason={}", lockFile, it.message) }
	}
}
