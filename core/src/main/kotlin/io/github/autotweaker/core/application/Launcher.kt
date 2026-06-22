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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.dev.Debugger
import io.github.autotweaker.api.trace.catching
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
import io.github.autotweaker.core.infrastructure.persistence.WorkspaceManager
import io.github.autotweaker.core.infrastructure.persistence.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.session.AgentDataDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionDataDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionMessageDbApi
import io.github.autotweaker.core.infrastructure.persistence.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import io.github.autotweaker.core.infrastructure.persistence.store.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object Launcher : Loggable, Traceable {
	private val databaseStore: DatabaseStore = H2DatabaseStore
	
	suspend fun start(
		version: SemVer,
		registry: MutableMap<String, Pair<Adapter, AdapterInfo>>,
		adapterAPI: CoreAPI.AdapterAPI,
	) {
		initServices(
			ServiceRegistry(
				trace = TraceRecorderImpl::recorder,
				store = JsonStoreImpl::namespace,
				setting = Settings,
				i18n = I18nServiceImpl,
			)
		)
		
		JsonStoreImpl.init(databaseStore)
		Settings.init(databaseStore)
		SessionRepositoryImpl.init(databaseStore)
		SettingDbApi.init(databaseStore)
		JsonStoreDbApi.init(databaseStore)
		SessionDataDbApi.init(databaseStore)
		AgentDataDbApi.init(databaseStore)
		SessionMessageDbApi.init(databaseStore)
		SecretManager.init()
		DbDebugAPIImpl.init(databaseStore)
		TraceStore.init(databaseStore)
		TraceRecorderImpl.init()
		WorkspaceManager.init()
		
		PluginLoader.load<Debugger>().forEach { debugger ->
			log.info("Initialized debugger  class={}", debugger::class.java.name)
			debugger.init(DbDebugAPIImpl)
		}
		
		Wiring.init()
		
		TranslationManager.init(ModelRepositoryImpl)
		TranslationManager.startTranslation()
		
		val all = PluginLoader.load<Adapter>().map { it to it.load(version) }
		val adapters =
			all.groupBy { (_, info) -> info.name }.map { (_, pairs) -> pairs.maxBy { (_, info) -> info.version } }
		
		if (!adapters.isEmpty()) {
			log.info("Found adapters to start  count={}", adapters.size)
			adapters.forEach { (adapter, info) ->
				registry[info.name] = adapter to info
				log.info(
					"Loaded adapter  name={}  version={}  description={}", info.name, info.version, info.description
				)
				adapter.start(createCoreAPI(adapterAPI))
				log.info("Started adapter  name={}", info.name)
			}
		}
	}
	
	fun createCoreAPI(adapterAPI: CoreAPI.AdapterAPI) =
		CoreAPIImpl(adapterAPI, EnvConfigAPI, ProviderConfigAPI, ModelConfigAPI, ApiKeyConfigAPI)
	
	suspend fun shutdown(registry: List<Pair<Adapter, AdapterInfo>>) {
		coroutineScope {
			registry.forEach { (adapter, info) ->
				launch {
					trace.catching {
						adapter.stop()
						log.info("Stopped adapter  name={}", info.name)
					}.onFailure { log.warn("Failed adapter stop  name={}  reason={}", info.name, it.message) }
				}
			}
		}
		trace.catching { SessionManager.shutdown() }
			.onFailure { log.warn("Failed SessionManager shutdown") }
		trace.catching { ContainerManager.stop() }
			.onFailure { log.warn("Failed ContainerManager stop") }
		trace.catching { TranslationManager.shutdown() }
			.onFailure { log.warn("Failed TranslationManager shutdown") }
		trace.catching { AbstractOpenAiClient.close() }
			.onFailure { log.warn("Failed LLM client close") }
		trace.catching { SecretManager.killGpgAgent() }
			.onFailure { log.warn("Failed GPG agent kill") }
		trace.catching { databaseStore.shutdown() }
			.onFailure { log.warn("Failed DatabaseStore shutdown") }
		log.info("Completed launcher shutdown")
	}
}
