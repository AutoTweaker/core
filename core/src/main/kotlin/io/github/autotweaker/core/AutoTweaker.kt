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
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.application.Launcher
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object AutoTweaker : CoreAPI.AdapterAPI {
	private val logger = LoggerFactory.getLogger(this::class.java)
	val version: SemVer by lazy {
		val props = Properties()
		this::class.java.getResourceAsStream("/version.properties")?.use { props.load(it) }
		SemVer.parse(props.getProperty("version"))
	}
	
	private val builtInAdapters: List<Adapter> by lazy {
		ServiceLoader.load(Adapter::class.java).toList()
	}
	
	private val registry: MutableMap<String, Pair<Adapter, AdapterInfo>> = mutableMapOf()
	
	private val lockFile: Path = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "autotweaker.lock"
	)
	
	fun start() {
		Files.createDirectories(Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins"))
		acquireLock()
		
		logger.info("AutoTweaker started  version={}", version)
		
		Launcher.start(version, builtInAdapters, registry, this)
		Runtime.getRuntime().addShutdownHook(Thread {
			logger.info("AutoTweaker shutdown initiated")
			Launcher.shutdown(registry)
			PluginLoader.closeClassLoaders()
			lockFile.deleteIfExists()
			logger.info("AutoTweaker shutdown completed")
		})
	}
	
	private fun acquireLock() {
		Files.createDirectories(lockFile.parent)
		try {
			Files.createFile(lockFile)
		} catch (_: java.nio.file.FileAlreadyExistsException) {
			val pid = lockFile.readText().trim().toLongOrNull()
			if (pid != null && ProcessHandle.of(pid).isPresent) {
				throw IllegalStateException("Another instance is already running (pid=$pid)")
			}
			lockFile.deleteIfExists()
			Files.createFile(lockFile)
		}
		lockFile.writeText(ProcessHandle.current().pid().toString())
	}
	
	override fun listAdapter(): List<AdapterInfo> = registry.values.map { it.second }
	
	override fun startAdapter(name: String) {
		val (adapter, info) = requireAdapter(name)
		adapter.start(Launcher.createCoreAPI(this))
		logger.info("Started adapter  name={}", info.name)
	}
	
	override fun stopAdapter(name: String) {
		val (adapter, info) = requireAdapter(name)
		adapter.stop()
		logger.info("Stopped adapter  name={}", info.name)
	}
	
	private fun requireAdapter(name: String): Pair<Adapter, AdapterInfo> =
		registry[name] ?: error("Unknown adapter: $name")
}
