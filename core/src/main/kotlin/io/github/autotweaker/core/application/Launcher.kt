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
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.dev.Debugger
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.adapter.i18n.I18nServiceImpl
import io.github.autotweaker.core.adapter.i18n.translation.TranslationManager
import io.github.autotweaker.core.application.Wiring.databaseStore
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persist.db.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persist.db.config.Settings
import io.github.autotweaker.core.infrastructure.persist.db.session.AgentDataDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionDataDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionMessageDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceRecorderImpl
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceStore
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
object Launcher : Loggable, Traceable {
	suspend fun start(
		registry: MutableMap<KebabCase, Pair<Adapter, AdapterInfo>>,
		lazyCore: () -> CoreAPI
	) {
		//依赖最广泛的able api
		initServices(
			ServiceRegistry(
				trace = TraceRecorderImpl::recorder,
				store = JsonStoreImpl::namespace,
				lazySetting = { Settings },
				lazyI18n = { I18nServiceImpl },
			)
		)
		
		
		//都是数据库IO，互不依赖
		JsonStoreImpl.init(databaseStore)
		Settings.init(databaseStore)
		TraceStore.init(databaseStore)
		SessionRepositoryImpl.init(databaseStore)
		//DbApi
		SettingDbApi.init(databaseStore)
		JsonStoreDbApi.init(databaseStore)
		SessionDataDbApi.init(databaseStore)
		AgentDataDbApi.init(databaseStore)
		SessionMessageDbApi.init(databaseStore)
		
		
		//密钥库
		SecretManager.init()
		//依赖SecretManager
		DbDebugAPIImpl.init(databaseStore)
		
		//Trace服务，会启动协程
		TraceRecorderImpl.init()
		
		//缓存初始化
		WorkspaceManager.init()
		
		//创建目录、检查权限、开始拉镜像
		ContainerManager.init()
		
		//都是纯赋值
		Wiring.init()
		
		PluginLoader.load<Debugger>().forEach { debugger ->
			log.info("Initialized debugger  class={}", debugger::class.java.name)
			debugger.init(DbDebugAPIImpl)
		}
		
		val all = PluginLoader.load<Adapter>().map { it to it.init(lazyCore()) }
		val adapters = all.groupBy { (_, info) -> info.name }
			.map { (_, pairs) -> pairs.maxBy { (_, info) -> info.version } }
		
		if (!adapters.isEmpty()) {
			log.info("Found adapters to start  count={}", adapters.size)
			adapters.forEach { (adapter, info) ->
				registry[info.name] = adapter to info
				log.info(
					"Loaded adapter  name={}  version={}  description={}",
					info.name,
					info.version,
					info.description
				)
				adapter.start()
				log.info("Started adapter  name={}", info.name)
			}
		}
		
		TranslationManager.startTranslation()
	}
	
	suspend fun shutdown(registry: PairList<Adapter, AdapterInfo>) {
		registry.forEachParallel { (adapter, info) ->
			trace.catching {
				adapter.stop()
				log.info("Stopped adapter  name={}", info.name)
			}.onFailure { log.warn("Failed adapter stop  name={}  reason={}", info.name, it.message) }
		}
		
		trace.catching { I18nServiceImpl.shutdown() }
			.onFailure { log.warn("Failed I18nServiceImpl shutdown") }
		trace.catching { SessionManager.shutdown() }
			.onFailure { log.warn("Failed SessionManager shutdown") }
		trace.catching { ContainerManager.stop() }
			.onFailure { log.warn("Failed ContainerManager stop") }
		trace.catching { TranslationManager.shutdown() }
			.onFailure { log.warn("Failed TranslationManager shutdown") }
		
		LlmClientLoader.shutdown()
		
		trace.catching { SecretManager.killGpgAgent() }
			.onFailure { log.warn("Failed GPG agent kill") }
		trace.catching { TraceRecorderImpl.shutdown() }
			.onFailure { log.warn("Failed TraceRecorderImpl shutdown") }
		trace.catching { databaseStore.shutdown() }
			.onFailure { log.warn("Failed DatabaseStore shutdown") }
		
		log.info("Completed launcher shutdown")
	}
}
