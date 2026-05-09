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

import io.github.autotweaker.core.adapter.api.AdapterAPI
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.AdapterInfo
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.secret.SecretManager
import org.slf4j.LoggerFactory
import java.util.*

object AutoTweaker {
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
		val external = loadPlugins<AdapterAPI>("adapter")
		val externalNames = external.map { it.load(version).name }.toSet()
		external + builtInAdapters.filter { it.load(version).name !in externalNames }
	}
	
	private val registry: MutableMap<String, Pair<AdapterAPI, AdapterInfo>> = mutableMapOf()
	
	fun start() {
		logger.info("AutoTweaker  Copyright (C) 2026  WhiteElephant-abc")
		logger.info("AutoTweaker started  version={}", version)
		
		Settings.init()
		JsonStore.init()
		try {
			SecretManager.init()
		} catch (e: Exception) {
			logger.error("Failed to initialize SecretManager", e)
			throw e
		}
		
		val core: CoreAPI = CoreAPIImpl
		
		val adapters = allAdapters
		if (adapters.isEmpty()) {
			val noAdapterError =
				IllegalStateException("No AdapterAPI implementations found. At least one adapter is required.")
			logger.error("Failed to load any adapter", noAdapterError)
			throw noAdapterError
		}
		
		logger.info(
			"Found {} adapters to start  builtIn={}  external={}",
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
			adapter.start(core)
			logger.info("Adapter started  name={}", info.name)
		}
	}
	
	fun listAdapter(): List<AdapterInfo> = registry.values.map { it.second }
	
	fun startAdapter(name: String) {
		val (adapter, info) = registry[name] ?: error("Unknown adapter: $name")
		adapter.start(CoreAPIImpl)
		logger.info("Started adapter  name={}", info.name)
	}
	
	fun stopAdapter(name: String) {
		val (adapter, info) = registry[name] ?: error("Unknown adapter: $name")
		adapter.stop()
		logger.info("Stopped adapter  name={}", info.name)
	}
}
