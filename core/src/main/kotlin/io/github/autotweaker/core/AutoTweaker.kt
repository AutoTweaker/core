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
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.secret.SecretManager
import org.slf4j.LoggerFactory
import java.util.*

object AutoTweaker {
	private val logger = LoggerFactory.getLogger(AutoTweaker::class.java)
	val version = SemVer(1, 0, 0)
	
	private val builtInAdapters: List<AdapterAPI> by lazy {
		ServiceLoader.load(AdapterAPI::class.java).toList()
	}
	
	private val allAdapters: List<AdapterAPI> by lazy {
		val external = loadPlugins<AdapterAPI>("adapter")
		val externalNames = external.map { it.load(version).name }.toSet()
		external + builtInAdapters.filter { it.load(version).name !in externalNames }
	}
	
	fun start() {
		logger.info("AutoTweaker $version starting...")
		logger.info("AutoTweaker  Copyright (C) 2026  WhiteElephant-abc")
		
		Settings.init()
		logger.info("Settings initialized")
		
		JsonStore.init()
		logger.info("JsonStore initialized")
		
		SecretManager
		logger.info(
			if (SecretManager.isUnlocked) "SecretManager auto-unlocked"
			else "SecretManager locked — waiting for password"
		)
		
		val core: CoreAPI = CoreAPIImpl
		logger.info("CoreAPI ready")
		
		val adapters = allAdapters
		if (adapters.isEmpty()) {
			error("No AdapterAPI implementations found. At least one adapter is required.")
		}
		
		adapters.forEach { adapter ->
			val info = adapter.load(version)
			logger.info("Adapter loaded: ${info.name} v${info.version} — ${info.description}")
			adapter.start(core)
			logger.info("Adapter started: ${info.name}")
		}
	}
}