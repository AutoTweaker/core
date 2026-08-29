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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.debug.DbDebugAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.store.ObjectStorage
import io.github.autotweaker.core.application.impl.ChatService
import io.github.autotweaker.core.application.impl.CoreAPIImpl
import io.github.autotweaker.core.application.impl.DbDebugAPIImpl
import io.github.autotweaker.core.application.impl.ShellRouter
import io.github.autotweaker.core.domain.agent.AgentDeps
import io.github.autotweaker.core.domain.agent.chat.AgentChat
import io.github.autotweaker.core.domain.agent.chat.MessageConverts
import io.github.autotweaker.core.domain.agent.compact.SummaryService
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.*
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyRepository
import io.github.autotweaker.core.infrastructure.config.EnvRepository
import io.github.autotweaker.core.infrastructure.config.ModelConfigRepository
import io.github.autotweaker.core.infrastructure.config.ProviderRepository
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.container.ContainerService
import io.github.autotweaker.core.infrastructure.container.PathResolverImpl
import io.github.autotweaker.core.infrastructure.container.docker.DockerJavaService
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import io.github.autotweaker.core.infrastructure.data.SecretDbApi
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.data.TemporaryStorageImpl
import io.github.autotweaker.core.infrastructure.git.GitStatusServiceImpl
import io.github.autotweaker.core.infrastructure.i18n.I18nServiceImpl
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationEngine
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationManager
import io.github.autotweaker.core.infrastructure.llm.LlmGatewayImpl
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.config.SettingDbApi
import io.github.autotweaker.core.infrastructure.persist.db.config.Settings
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreDbApi
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persist.db.objstore.ObjectStorageImpl
import io.github.autotweaker.core.infrastructure.persist.db.session.AgentDataDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionDataDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionMessageDbApi
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceCleanup
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceRecorderImpl
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceStore
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageDbApi
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageRepositoryImpl
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.system.LocalShellExecutor
import io.github.autotweaker.core.infrastructure.system.RawFileSystemImpl
import io.github.autotweaker.core.infrastructure.system.SystemInfoServiceImpl
import org.koin.core.Koin
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

object Wiring : Loggable {
	fun createKoin(): Koin = startKoin {
		modules(persistModule, configModule, domainModule, appModule)
	}.koin
	
	val appModule = module {
		singleOf(::TranslationEngine)
		singleOf(::TranslationManager)
		singleOf(::DbDebugAPIImpl).bind<DbDebugAPI>()
		factory<CoreAPI> { (adapter: CoreAPI.AdapterAPI) ->
			CoreAPIImpl(
				usageRepository = get(),
				sessionManager = get(),
				containerManager = get(),
				envRepository = get(),
				providerRepository = get(),
				modelConfigRepository = get(),
				modelResolverImpl = get(),
				apiKeyRepository = get(),
				settings = get(),
				translationManager = get(),
				chatService = get(),
				traceStore = get(),
				shellRouter = get(),
				adapter = adapter,
				pathResolver = get(),
				appVersion = ResourcesLoader.version
			)
		}
	}
	
	val domainModule = module {
		single<LlmGateway> { LlmGatewayImpl }
		single<RawFileSystem> { RawFileSystemImpl }
		single<PathResolver> { PathResolverImpl }
		single<TemporaryStorage> { TemporaryStorageImpl }
		single<SystemInfoService> { SystemInfoServiceImpl }
		single<GitStatusService> { GitStatusServiceImpl }
		singleOf(::LocalShellExecutor)
		singleOf(::ResilientChat)
		singleOf(::AgentChat)
		singleOf(::SummaryService)
		singleOf(::MessageConverts)
		singleOf(::ToolProvider)
		singleOf(::AgentDeps)
		singleOf(::SessionManager)
		singleOf(::ChatService)
		singleOf(::ShellRouter).bind<ShellExecutor>()
	}
	
	val configModule = module {
		single { SecretManager }.bind<SecretStore>()
		single { I18nServiceImpl }.bind<I18nService>()
		singleOf(::SecretDbApi)
		singleOf(::DockerJavaService).bind<ContainerService>()
		singleOf(::ContainerManager)
		singleOf(::ApiKeyRepository)
		singleOf(::EnvRepository)
		singleOf(::ModelResolverImpl).bind<ModelResolver>()
		singleOf(::ModelConfigRepository)
		singleOf(::ProviderRepository)
	}
	
	val persistModule = module {
		single<DatabaseStore> { H2DatabaseStore }
		singleOf(::Settings).bind<SettingService>()
		singleOf(::JsonStoreImpl)
		singleOf(::ObjectStorageImpl).bind<ObjectStorage>()
		singleOf(::TraceStore)
		singleOf(::SessionRepositoryImpl).bind<SessionRepository>()
		singleOf(::UsageRepositoryImpl).bind<UsageRepository>()
		singleOf(::SettingDbApi)
		singleOf(::JsonStoreDbApi)
		singleOf(::SessionDataDbApi)
		singleOf(::AgentDataDbApi)
		singleOf(::SessionMessageDbApi)
		singleOf(::UsageDbApi)
		singleOf(::TraceCleanup)
		singleOf(::TraceRecorderImpl)
	}
}
