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

import io.github.autotweaker.api.AdapterRegistry
import io.github.autotweaker.api.CoreAPI
import io.github.autotweaker.api.LlmClient
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.model.ModelId
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.adapter.config.ConfigManager
import io.github.autotweaker.core.data.json.JsonStoreImpl
import io.github.autotweaker.core.secret.impl.SecretManager
import io.github.autotweaker.core.session.SessionManager
import io.github.autotweaker.core.session.WorkspaceAPI
import java.util.*

class CoreAPIImpl(private val adapterRegistry: AdapterRegistry) : CoreAPI {
	override fun changePassword(oldPassword: String, newPassword: String) =
		SecretManager.changePassword(oldPassword, newPassword)
	
	override fun unlock(password: String) = SecretManager.unlock(password)
	override fun listAdapter(): List<AdapterInfo> = adapterRegistry.listAdapter()
	override fun startAdapter(name: String) = adapterRegistry.startAdapter(name)
	override fun stopAdapter(name: String) = adapterRegistry.stopAdapter(name)
	override fun jsonStore(namespace: String) = JsonStoreImpl.namespace(namespace)
	override val isUnlocked: Boolean get() = SecretManager.isUnlocked
	override val isPasswordEmpty: Boolean get() = SecretManager.isPasswordEmpty
	
	override val session = object : CoreAPI.SessionAPI {
		override suspend fun create(config: SessionConfig) = SessionManager.create(config)
		override suspend fun create(workspaceId: UUID, config: SessionConfig) =
			SessionManager.create(workspaceId, config)
		
		override suspend fun delete(sessionId: UUID) = SessionManager.delete(sessionId)
		override suspend fun send(sessionId: UUID, content: String, images: List<Base64>?) =
			SessionManager.send(sessionId, content, images)
		
		override suspend fun stop(sessionId: UUID) = SessionManager.stop(sessionId) ?: Unit
		override fun pause(sessionId: UUID) = SessionManager.pauseAgent(sessionId) ?: Unit
		override fun resume(sessionId: UUID) = SessionManager.resumeAgent(sessionId) ?: Unit
		override fun cancel(sessionId: UUID) = SessionManager.cancelAgent(sessionId) ?: Unit
		override fun retry(sessionId: UUID) = SessionManager.retryAgent(sessionId) ?: Unit
		override fun compact(sessionId: UUID) = SessionManager.compactAgent(sessionId) ?: Unit
		override fun approveToolCall(sessionId: UUID, approvals: List<ToolApprove>) =
			SessionManager.approveToolCall(sessionId, approvals)
		
		override fun getHandle(sessionId: UUID) = SessionManager.get(sessionId)
		override fun updateTitle(sessionId: UUID, title: String) = SessionManager.updateTitle(sessionId, title)
		override fun updateConfig(sessionId: UUID, config: SessionConfig) =
			SessionManager.updateConfig(sessionId, config) ?: Unit
		
		override suspend fun loadData(ids: List<UUID>) = SessionManager.loadData(ids)
		override suspend fun loadContext(sessionId: UUID) = SessionManager.loadContext(sessionId)
		override suspend fun loadMessages(ids: List<UUID>) = SessionManager.loadMessages(ids)
		
		override fun createWorkspace(meta: WorkspaceMeta) = WorkspaceAPI.create(meta)
		override suspend fun renameWorkspace(id: UUID, newName: String) = WorkspaceAPI.rename(id, newName)
		override suspend fun deleteWorkspace(id: UUID) = WorkspaceAPI.delete(id)
		override fun listWorkspaces() = WorkspaceAPI.list()
	}
	
	override val config = object : CoreAPI.ConfigAPI {
		private val cfg = ConfigManager
		override fun getAppConfig(key: SettingKey) = cfg.appConfig.get(key)
		override fun setAppConfig(config: CoreConfig.AppConfig) = cfg.appConfig.set(config)
		override fun getAllAppConfigs() = cfg.appConfig.getAll()
		override fun listEnv(type: CoreConfig.JsonConfig.Env.Type) = cfg.envConfig.list(type)
		override fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = cfg.envConfig.get(type, id)
		override fun setEnv(env: List<CoreConfig.JsonConfig.Env>) = cfg.envConfig.set(env)
		override fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = cfg.envConfig.remove(type, id)
		override fun listProviders() = cfg.providerConfig.list()
		override fun listAvailableProviderTypes() = cfg.providerConfig.listAvailable()
		override fun getProviderMeta(type: String): LlmClient.ProviderInfo = cfg.providerConfig.getMeta(type)
		override fun addProvider(provider: CoreConfig.ProviderConfig.Provider) = cfg.providerConfig.create(provider)
		override fun removeProvider(name: String) = cfg.providerConfig.delete(name)
		override fun renameProvider(name: String, new: String) = cfg.providerConfig.rename(name, new)
		override fun setProviderType(name: String, type: String) = cfg.providerConfig.updateType(name, type)
		override fun setProviderKey(name: String, keyName: String) = cfg.providerConfig.updateKey(name, keyName)
		override fun setProviderUrl(name: String, url: Url) = cfg.providerConfig.updateUrl(name, url)
		override fun setProviderRule(name: String, rules: List<ProviderData.ErrorHandlingRule>) =
			cfg.providerConfig.updateRule(name, rules)
		
		override fun listModels() = cfg.modelConfig.list()
		override fun listModelIds() = cfg.modelConfig.listId()
		override fun getModelMeta(provider: String, modelId: String): ProviderData.ModelData.ModelInfo? =
			cfg.modelConfig.getMeta(provider, modelId)
		
		override fun addModel(model: CoreConfig.ProviderConfig.Model) = cfg.modelConfig.add(model)
		override fun removeModel(id: ModelId) = cfg.modelConfig.remove(id)
		override fun setModel(id: ModelId, model: CoreConfig.ProviderConfig.Model) = cfg.modelConfig.update(id, model)
		override fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey) = cfg.apiKeyConfig.add(key)
		override fun listApiKeyNames() = cfg.apiKeyConfig.list()
		override fun removeApiKey(name: String) = cfg.apiKeyConfig.delete(name)
	}
}
