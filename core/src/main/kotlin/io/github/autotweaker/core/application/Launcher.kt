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
import io.github.autotweaker.api.dev.Debugger
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.adapter.i18n.I18nServiceImpl
import io.github.autotweaker.core.adapter.i18n.translation.TranslationManager
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyConfigAPI
import io.github.autotweaker.core.infrastructure.config.EnvConfigAPI
import io.github.autotweaker.core.infrastructure.config.ModelConfigAPI
import io.github.autotweaker.core.infrastructure.config.ProviderConfigAPI
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.llm.openai.AbstractOpenAiClient
import io.github.autotweaker.core.infrastructure.persistence.ModelRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.session.SessionContextDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionDataDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionMessageDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import io.github.autotweaker.core.infrastructure.persistence.store.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceStore
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object Launcher {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val databaseStore: DatabaseStore = H2DatabaseStore
	
	suspend fun start(
		version: SemVer,
		registry: MutableMap<String, Pair<Adapter, AdapterInfo>>,
		adapterAPI: CoreAPI.AdapterAPI,
	) {
		JsonStoreImpl.init(databaseStore)
		Settings.init(databaseStore)
		SessionRepositoryImpl.init(databaseStore)
		SettingDbApi.init(databaseStore)
		JsonStoreDbApi.init(databaseStore)
		SessionDataDbApi.init(databaseStore)
		SessionContextDbApi.init(databaseStore)
		SessionMessageDbApi.init(databaseStore)
		SecretManager.init(Settings)
		DbDebugAPIImpl.init(databaseStore)
		TraceStore.init(databaseStore)
		
		PluginLoader.load<Debugger>().forEach { debugger ->
			logger.info("Initializing debugger  class={}", debugger::class.java.name)
			debugger.init(DbDebugAPIImpl)
		}
		
		Wiring.init()
		
		TranslationManager.init(ModelRepositoryImpl, Settings, I18nServiceImpl)
		TranslationManager.startTranslation()
		
		val all = PluginLoader.load<Adapter>().map { it to it.load(version) }
		val adapters =
			all.groupBy { (_, info) -> info.name }.map { (_, pairs) -> pairs.maxBy { (_, info) -> info.version } }
		
		if (!adapters.isEmpty()) {
			logger.info("Found adapters to start  count={}", adapters.size)
			adapters.forEach { (adapter, info) ->
				registry[info.name] = adapter to info
				logger.info(
					"Adapter loaded  name={}  version={}  description={}", info.name, info.version, info.description
				)
				adapter.start(createCoreAPI(adapterAPI))
				logger.info("Started adapter  name={}", info.name)
			}
		}
	}
	
	fun createCoreAPI(adapterAPI: CoreAPI.AdapterAPI) =
		CoreAPIImpl(adapterAPI, EnvConfigAPI, ProviderConfigAPI, ModelConfigAPI, ApiKeyConfigAPI)
	
	fun shutdown(registry: Map<String, Pair<Adapter, AdapterInfo>>) {
		registry.values.forEach { (adapter, info) ->
			runBlocking {
				runCatching {
					adapter.stop()
					logger.info("Stopped adapter  name={}", info.name)
				}.onFailure { e -> logger.warn("Failed to stop adapter  name={}  reason={}", info.name, e.message) }
			}
		}
		runBlocking {
			runCatching {
				SessionManager.shutdown()
			}.onFailure { logger.warn("Failed to shutdown SessionManager") }
		}
		runBlocking {
			runCatching {
				ContainerManager.stop()
			}.onFailure { logger.warn("Failed to stop ContainerManager") }
		}
		runCatching {
			TranslationManager.shutdown()
		}.onFailure { logger.warn("Failed to shutdown TranslationManager") }
		runCatching {
			AbstractOpenAiClient.close()
		}.onFailure { logger.warn("Failed to close LLM clients") }
		runCatching {
			SecretManager.killGpgAgent()
		}.onFailure { logger.warn("Failed to kill GPG agent") }
		runCatching {
			databaseStore.shutdown()
		}.onFailure { logger.warn("Failed to shutdown DatabaseStore") }
		logger.info("Launcher shutdown completed")
	}
}
