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

package io.github.autotweaker.core.application

import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.adapter.i18n.I18nServiceImpl
import io.github.autotweaker.core.adapter.i18n.translation.TranslationManager
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyConfigAPI
import io.github.autotweaker.core.infrastructure.config.EnvConfigAPI
import io.github.autotweaker.core.infrastructure.config.ModelConfigAPI
import io.github.autotweaker.core.infrastructure.config.ProviderConfigAPI
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.llm.openai.AbstractOpenAiClient
import io.github.autotweaker.core.infrastructure.persistence.ModelRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.store.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.secret.impl.SecretManager
import io.github.autotweaker.core.loadPlugins
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object Launcher {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	fun start(
		version: SemVer,
		builtInAdapters: List<Adapter>,
		registry: MutableMap<String, Pair<Adapter, AdapterInfo>>,
		adapterAPI: CoreAPI.AdapterAPI,
	) {
		JsonStoreImpl.init()
		Settings.init()
		SecretManager.init(Settings)
		
		Wiring.init()
		
		TranslationManager.init(SessionRepositoryImpl, ModelRepositoryImpl, Settings, I18nServiceImpl)
		TranslationManager.startTranslation()
		
		val all = (builtInAdapters + loadPlugins<Adapter>()).map { it to it.load(version) }
		val adapters =
			all.groupBy { (_, info) -> info.name }.map { (_, pairs) -> pairs.maxBy { (_, info) -> info.version }.first }
		
		if (adapters.isEmpty()) {
			throw IllegalStateException("No Adapter implementations found. At least one adapter is required.")
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
			adapter.start(createCoreAPI(adapterAPI))
			logger.info("Started adapter  name={}", info.name)
		}
	}
	
	fun createCoreAPI(adapterAPI: CoreAPI.AdapterAPI) =
		CoreAPIImpl(adapterAPI, EnvConfigAPI, ProviderConfigAPI, ModelConfigAPI, ApiKeyConfigAPI)
	
	fun shutdown(registry: Map<String, Pair<Adapter, AdapterInfo>>) {
		registry.values.forEach { (adapter, _) ->
			runCatching { adapter.stop() }
		}
		runBlocking { runCatching { SessionManager.shutdown() } }
		runBlocking { runCatching { ContainerManager.stop() } }
		runCatching { AbstractOpenAiClient.close() }
		runCatching { SecretManager.killGpgAgent() }
		runCatching { H2DatabaseStore.shutdown() }
	}
}
