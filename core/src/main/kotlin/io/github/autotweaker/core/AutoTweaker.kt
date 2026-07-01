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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.dev.StartupHook
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.application.Launcher
import io.github.autotweaker.core.application.Wiring
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object AutoTweaker : CoreAPI.AdapterAPI, Loggable, Traceable {
	private val registry: MutableMap<KebabCase, Pair<Adapter, AdapterInfo>> = mutableMapOf()
	private val lock = ReentrantMutex()
	
	private val core by lazy { Wiring.createCoreAPI(this) }
	
	private val lockFile: Path = CONFIG_PATH.resolve("$APP_NAME_LOWERCASE.lock")
	private var lockChannel: FileChannel? = null
	private var fileLock: FileLock? = null
	
	suspend fun start() {
		withContext(Dispatchers.IO) {
			Files.createDirectories(CONFIG_PATH.resolve("plugins"))
			acquireLock()
		}
		
		PluginLoader.load<StartupHook>().forEach { hook ->
			hook.execute(ResourcesLoader.version)
			log.info("Executed startup hook  class={}", hook::class.java.name)
		}
		
		log.info("Started AutoTweaker  version={}", ResourcesLoader.version)
		
		Launcher.start(registry) { core }
	}
	
	fun shutdown() {
		log.info("Initiated AutoTweaker shutdown")
		runBlocking { Launcher.shutdown(registry.values.toList()) }
		PluginLoader.closeClassLoaders()
		releaseLock()
		log.info("Completed AutoTweaker shutdown")
	}
	
	private fun acquireLock() {
		Files.createDirectories(lockFile.parent)
		val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
		lockChannel = channel
		val lock = channel.tryLock()
		if (lock == null) {
			channel.close()
			error("Another instance is already running")
		}
		fileLock = lock
		channel.truncate(0)
		channel.write(ByteBuffer.wrap(ProcessHandle.current().pid().toString().toByteArray()))
		channel.force(true)
		log.debug("Acquired lock  pid={}  lockFile={}", ProcessHandle.current().pid(), lockFile)
	}
	
	override suspend fun list() = lock.withLock {
		registry.values.map { it.second to it.first.isRunning }
	}
	
	override suspend fun start(name: KebabCase) = lock.withLock {
		val (adapter, info) = requireAdapter(name)
		if (adapter.isRunning) return@withLock false
		adapter.start()
		log.info("Started adapter  name={}", info.name)
		return@withLock true
	}
	
	override suspend fun alive(name: KebabCase): Boolean = lock.withLock {
		val (adapter, _) = requireAdapter(name)
		return@withLock adapter.isRunning
	}
	
	override suspend fun stop(name: KebabCase) = lock.withLock {
		val (adapter, info) = requireAdapter(name)
		if (!adapter.isRunning) return@withLock false
		adapter.stop()
		log.info("Stopped adapter  name={}", info.name)
		return@withLock true
	}
	
	private fun requireAdapter(name: KebabCase): Pair<Adapter, AdapterInfo> =
		requireNotNull(registry[name]) { "Unknown adapter: $name" }
	
	private fun releaseLock() {
		trace.catching {
			fileLock?.release()
			lockChannel?.close()
			Files.deleteIfExists(lockFile)
		}.onFailure { log.warn("Failed lock release  lockFile={}  reason={}", lockFile, it.message) }
	}
}
