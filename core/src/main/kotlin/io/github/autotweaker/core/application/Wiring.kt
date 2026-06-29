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
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.core.adapter.i18n.translation.TranslationManager
import io.github.autotweaker.core.adapter.impl.CoreAPIImpl
import io.github.autotweaker.core.application.chat.ChatService
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyConfigAPI
import io.github.autotweaker.core.infrastructure.config.EnvConfigAPI
import io.github.autotweaker.core.infrastructure.config.ModelConfigAPI
import io.github.autotweaker.core.infrastructure.config.ProviderConfigAPI
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.container.PathResolverImpl
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.llm.LlmGatewayImpl
import io.github.autotweaker.core.infrastructure.persist.EnvStore
import io.github.autotweaker.core.infrastructure.persist.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.tool.RawFileSystemImpl

object Wiring : Loggable {
	val pathResolver = PathResolverImpl(ContainerConfig())
	
	/**
	 * 都是纯赋值
	 */
	fun init() {
		TranslationManager.init(ModelResolverImpl)
		EnvStore.init(SecretManager)
		ModelResolverImpl.init(SecretManager)
		ApiKeyConfigAPI.init(SecretManager)
		ResilientChat.init(LlmGatewayImpl)
		ChatService.init(
			ModelResolverImpl, SessionRepositoryImpl
		)
		SessionManager.init(SessionRepositoryImpl, ModelResolverImpl, SecretManager)
		ToolProvider.init(ShellRouter, RawFileSystemImpl, pathResolver)
		
		log.info("Completed wiring")
	}
	
	fun createCoreAPI(adapterAPI: CoreAPI.AdapterAPI, version: SemVer) = CoreAPIImpl(
		envRepo = EnvConfigAPI,
		providerRepo = ProviderConfigAPI,
		modelRepo = ModelConfigAPI,
		apiKeyRepo = ApiKeyConfigAPI,
		adapter = adapterAPI,
		pathResolver = pathResolver,
		appVersion = version
	)
}
