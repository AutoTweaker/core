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

package io.github.autotweaker.core.adapter.impl

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.config.ConfigManager
import io.github.autotweaker.core.adapter.config.CoreConfig
import io.github.autotweaker.core.data.provider.Provider
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.secret.SecretManager
import io.github.autotweaker.core.session.ModelId
import io.github.autotweaker.core.session.SessionConfig
import io.github.autotweaker.core.session.SessionManager
import io.github.autotweaker.core.session.workspace.WorkspaceMeta
import java.util.*

object CoreAPIImpl : CoreAPI {
	override fun unlock(password: String) = SecretManager.unlock(password)
	override val isUnlocked: Boolean get() = SecretManager.isUnlocked
	
	override val session = object : CoreAPI.SessionAPI {
		override suspend fun create(workspace: String, config: SessionConfig) =
			SessionManager.SessionAPI.create(workspace, config)
		
		override suspend fun delete(sessionId: UUID) = SessionManager.SessionAPI.delete(sessionId)
		
		override suspend fun send(sessionId: UUID, content: String, images: List<Base64>?) =
			SessionManager.SessionAPI.send(sessionId, content, images)
		
		override suspend fun stop(sessionId: UUID) {
			SessionManager.SessionAPI.stop(sessionId)
		}
		
		override fun pause(sessionId: UUID) {
			SessionManager.SessionAPI.pauseAgent(sessionId)
		}
		
		override fun resume(sessionId: UUID) {
			SessionManager.SessionAPI.resumeAgent(sessionId)
		}
		
		override fun cancel(sessionId: UUID) {
			SessionManager.SessionAPI.cancelAgent(sessionId)
		}
		
		override fun retry(sessionId: UUID) {
			SessionManager.SessionAPI.retryAgent(sessionId)
		}
		
		override fun compact(sessionId: UUID) {
			SessionManager.SessionAPI.compactAgent(sessionId)
		}
		
		override fun list() = SessionManager.SessionAPI.list()
		
		override fun updateTitle(sessionId: UUID, title: String) =
			SessionManager.SessionAPI.updateTitle(sessionId, title)
		
		override fun updateConfig(sessionId: UUID, config: SessionConfig) {
			SessionManager.SessionAPI.updateConfig(sessionId, config)
		}
		
		override fun createWorkspace(meta: WorkspaceMeta) = SessionManager.WorkspaceAPI.create(meta)
		
		override suspend fun renameWorkspace(name: String, newName: String) =
			SessionManager.WorkspaceAPI.updateName(name, newName)
		
		override suspend fun deleteWorkspace(name: String) = SessionManager.WorkspaceAPI.delete(name)
		
		override fun listWorkspaces(): List<WorkspaceMeta> = SessionManager.WorkspaceAPI.list()
	}
	
	override val config = object : CoreAPI.ConfigAPI {
		override fun getAppConfig(key: SettingKey) = ConfigManager.AppConfigAPI.get(key)
		
		override fun setAppConfig(config: CoreConfig.AppConfig) = ConfigManager.AppConfigAPI.set(config)
		
		override fun getAllAppConfigs() = ConfigManager.AppConfigAPI.getAll()
		
		override fun listEnv(type: CoreConfig.JsonConfig.Env.Type) = ConfigManager.EnvConfigAPI.list(type)
		
		override fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = ConfigManager.EnvConfigAPI.get(type, id)
		
		override fun setEnv(env: List<CoreConfig.JsonConfig.Env>) = ConfigManager.EnvConfigAPI.set(env)
		
		override fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) =
			ConfigManager.EnvConfigAPI.remove(type, id)
		
		override fun listProviders() = ConfigManager.ProviderConfigAPI.ProviderAPI.list()
		
		override fun createProvider(provider: CoreConfig.ProviderConfig.Provider) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.create(provider)
		
		override fun deleteProvider(name: String) = ConfigManager.ProviderConfigAPI.ProviderAPI.delete(name)
		
		override fun renameProvider(name: String, new: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.rename(name, new)
		
		override fun updateProviderType(name: String, type: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateType(name, type)
		
		override fun updateProviderKey(name: String, keyName: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateKey(name, keyName)
		
		override fun updateProviderUrl(name: String, url: Url) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateUrl(name, url)
		
		override fun updateProviderRule(name: String, rules: List<Provider.ErrorHandlingRule>) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateRule(name, rules)
		
		override fun listModels() = ConfigManager.ProviderConfigAPI.ModelAPI.list()
		
		override fun listModelIds() = ConfigManager.ProviderConfigAPI.ModelAPI.listId()
		
		override fun addModel(model: CoreConfig.ProviderConfig.Model) =
			ConfigManager.ProviderConfigAPI.ModelAPI.add(model)
		
		override fun removeModel(id: ModelId) = ConfigManager.ProviderConfigAPI.ModelAPI.remove(id)
		
		override fun updateModel(id: ModelId, model: CoreConfig.ProviderConfig.Model) =
			ConfigManager.ProviderConfigAPI.ModelAPI.update(id, model)
		
		override fun setApiKey(key: CoreConfig.ProviderConfig.ApiKey) =
			ConfigManager.ProviderConfigAPI.ApiKeyAPI.set(key)
		
		override fun listApiKeyNames() = ConfigManager.ProviderConfigAPI.ApiKeyAPI.list()
	}
}