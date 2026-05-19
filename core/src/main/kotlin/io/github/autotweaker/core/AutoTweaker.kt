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

import io.github.autotweaker.api.adapter.AdapterAPI
import io.github.autotweaker.api.adapter.AdapterRegistry
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.container.ContainerManager
import io.github.autotweaker.core.data.json.JsonStoreImpl
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import io.github.autotweaker.core.llm.base.openai.AbstractOpenAiClient
import io.github.autotweaker.core.secret.impl.SecretManager
import io.github.autotweaker.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object AutoTweaker : AdapterRegistry {
	private val logger = LoggerFactory.getLogger(this::class.java)
	val version: SemVer by lazy {
		val props = Properties()
		this::class.java.getResourceAsStream("/version.properties")?.use { props.load(it) }
		SemVer.parse(props.getProperty("version"))
	}
	
	private val builtInAdapters: List<AdapterAPI> by lazy {
		ServiceLoader.load(AdapterAPI::class.java).toList()
	}
	
	private val allAdapters: List<AdapterAPI> by lazy {
		val external = loadPlugins<AdapterAPI>()
		val externalNames = external.map { it.load(version).name }.toSet()
		external + builtInAdapters.filter { it.load(version).name !in externalNames }
	}
	
	private val registry: MutableMap<String, Pair<AdapterAPI, AdapterInfo>> = mutableMapOf()
	
	private val lockFile: Path = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "autotweaker.lock"
	)
	
	fun start() {
		Files.createDirectories(Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins"))
		acquireLock()
		
		logger.info("AutoTweaker started  version={}", version)
		
		JsonStoreImpl.init()
		Settings.init()
		try {
			SecretManager.init()
		} catch (e: Exception) {
			logger.error("Failed to initialize SecretManager", e)
			throw e
		}
		
		val adapters = allAdapters
		if (adapters.isEmpty()) {
			val noAdapterError =
				IllegalStateException("No AdapterAPI implementations found. At least one adapter is required.")
			logger.error("Failed to load any adapter", noAdapterError)
			throw noAdapterError
		}
		
		logger.info(
			"Found adapters to start  count={}  builtIn={}  external={}",
			adapters.size,
			builtInAdapters.size,
			adapters.size - builtInAdapters.size
		)
		adapters.forEach { adapter ->
			val info = adapter.load(version)
			registry[info.name] = adapter to info
			logger.info(
				"Adapter loaded  name={}  version={}  description={}", info.name, info.version, info.description
			)
			startAdapter(info.name)
		}
		
		Runtime.getRuntime().addShutdownHook(Thread {
			shutdown()
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
	
	private fun shutdown() {
		logger.info("AutoTweaker shutdown initiated")
		registry.values.forEach { (_, info) ->
			runCatching { stopAdapter(info.name) }
		}
		runBlocking { runCatching { SessionManager.shutdown() } }
		runBlocking { runCatching { ContainerManager.stop() } }
		runCatching { AbstractOpenAiClient.close() }
		runCatching { closePluginClassLoaders() }
		runCatching { SecretManager.killGpgAgent() }
		runCatching { H2DatabaseStore.shutdown() }
		runCatching { lockFile.deleteIfExists() }
		logger.info("AutoTweaker shutdown completed")
	}
	
	override fun listAdapter(): List<AdapterInfo> = registry.values.map { it.second }
	
	override fun startAdapter(name: String) {
		val (adapter, info) = requireAdapter(name)
		adapter.start(CoreAPIImpl(this))
		logger.info("Started adapter  name={}", info.name)
	}
	
	override fun stopAdapter(name: String) {
		val (adapter, info) = requireAdapter(name)
		adapter.stop()
		logger.info("Stopped adapter  name={}", info.name)
	}
	
	private fun requireAdapter(name: String): Pair<AdapterAPI, AdapterInfo> =
		registry[name] ?: error("Unknown adapter: $name")
}
