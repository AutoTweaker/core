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

import io.github.autotweaker.core.application.chat.ChatService
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.infrastructure.config.ApiKeyConfigAPI
import io.github.autotweaker.core.infrastructure.config.EnvConfigAPI
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.llm.LlmGatewayImpl
import io.github.autotweaker.core.infrastructure.persistence.ModelRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.session.SessionRepositoryImpl
import io.github.autotweaker.core.infrastructure.tool.RawFileSystemImpl
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log

object Wiring : Loggable {
	suspend fun init() {
		ModelRepositoryImpl.init(SecretManager)
		ApiKeyConfigAPI.init(SecretManager)
		EnvConfigAPI.init(SecretManager)
		ContainerManager.init(SecretManager)
		ResilientChat.init(LlmGatewayImpl, Settings)
		ChatService.init(
			ModelRepositoryImpl, SessionRepositoryImpl
		)
		SessionManager.init(SessionRepositoryImpl, ModelRepositoryImpl, SecretManager)
		ToolProvider.init(ShellRouter, RawFileSystemImpl)
		log.info("Initialized wiring")
	}
}