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
import io.github.autotweaker.api.log
import io.github.autotweaker.core.application.impl.ChatService
import io.github.autotweaker.core.application.impl.CoreAPIImpl
import io.github.autotweaker.core.application.impl.ShellRouter
import io.github.autotweaker.core.domain.agent.chat.MessageConverts
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.TruncationImpl
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyRepository
import io.github.autotweaker.core.infrastructure.container.PathResolverImpl
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import io.github.autotweaker.core.infrastructure.data.SecretDbApi
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.data.TemporaryStorageImpl
import io.github.autotweaker.core.infrastructure.git.GitStatusServiceImpl
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationManager
import io.github.autotweaker.core.infrastructure.llm.LlmGatewayImpl
import io.github.autotweaker.core.infrastructure.persist.db.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.persist.db.usage.UsageRepositoryImpl
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.store.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.system.SystemInfoServiceImpl
import io.github.autotweaker.core.infrastructure.tool.RawFileSystemImpl

object Wiring : Loggable {
	val databaseStore: DatabaseStore = H2DatabaseStore
	
	/**
	 * 都是纯赋值
	 */
	fun init() {
		TranslationManager.init(ModelResolverImpl)
		EnvStore.init(SecretManager)
		ApiKeyRepository.init(SecretManager)
		SecretDbApi.init(SecretManager)
		ModelResolverImpl.init(SecretManager)
		MessageConverts.init(RawFileSystemImpl, PathResolverImpl, SystemInfoServiceImpl, GitStatusServiceImpl)
		ResilientChat.init(LlmGatewayImpl)
		ChatService.init(
			ModelResolverImpl, SessionRepositoryImpl
		)
		SessionManager.init(SessionRepositoryImpl, UsageRepositoryImpl, ModelResolverImpl, SecretManager)
		TruncationImpl.init(PathResolverImpl, TemporaryStorageImpl)
		ToolProvider.init(ShellRouter, RawFileSystemImpl, PathResolverImpl, TemporaryStorageImpl)
		
		log.info("Completed wiring")
	}
	
	fun createCoreAPI(adapterAPI: CoreAPI.AdapterAPI) = CoreAPIImpl(
		usageRepo = UsageRepositoryImpl,
		adapter = adapterAPI,
		pathResolver = PathResolverImpl,
		appVersion = ResourcesLoader.version
	)
}
