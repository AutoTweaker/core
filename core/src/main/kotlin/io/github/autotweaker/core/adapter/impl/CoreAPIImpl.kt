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

import io.github.autotweaker.api.LlmClient
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.api.types.session.ModelId
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.AutoTweaker
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.config.ConfigManager
import io.github.autotweaker.core.agent.AgentCommand
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.secret.SecretManager
import io.github.autotweaker.core.session.SessionManager
import java.util.*

object CoreAPIImpl : CoreAPI {
	override fun changePassword(oldPassword: String, newPassword: String) =
		SecretManager.changePassword(oldPassword, newPassword)
	
	override fun unlock(password: String) = SecretManager.unlock(password)
	override fun listAdapter(): List<AdapterInfo> = AutoTweaker.listAdapter()
	override fun startAdapter(name: String) = AutoTweaker.startAdapter(name)
	override fun stopAdapter(name: String) = AutoTweaker.stopAdapter(name)
	override fun jsonStore(namespace: String) = JsonStore.namespace(namespace)
	override val isUnlocked: Boolean get() = SecretManager.isUnlocked
	override val isPasswordEmpty: Boolean get() = SecretManager.isPasswordEmpty
	
	override val session = object : CoreAPI.SessionAPI {
		override suspend fun create(workspace: String, config: SessionConfig) =
			SessionManager.SessionAPI.create(workspace, config)
		
		override suspend fun delete(sessionId: UUID) = SessionManager.SessionAPI.delete(sessionId)
		
		override suspend fun send(sessionId: UUID, content: String, images: List<Base64>?) =
			SessionManager.SessionAPI.send(sessionId, content, images)
		
		override suspend fun stop(sessionId: UUID) = SessionManager.SessionAPI.stop(sessionId) ?: Unit
		
		override fun pause(sessionId: UUID) = SessionManager.SessionAPI.pauseAgent(sessionId) ?: Unit
		
		override fun resume(sessionId: UUID) = SessionManager.SessionAPI.resumeAgent(sessionId) ?: Unit
		
		override fun cancel(sessionId: UUID) = SessionManager.SessionAPI.cancelAgent(sessionId) ?: Unit
		
		override fun retry(sessionId: UUID) = SessionManager.SessionAPI.retryAgent(sessionId) ?: Unit
		
		override fun compact(sessionId: UUID) = SessionManager.SessionAPI.compactAgent(sessionId) ?: Unit
		
		override fun approveToolCall(sessionId: UUID, approvals: List<AgentCommand.Message.ApproveToolCall.Approve>) =
			SessionManager.SessionAPI.approveToolCall(sessionId, approvals)
		
		override fun list() = SessionManager.SessionAPI.list()
		
		override fun updateTitle(sessionId: UUID, title: String) =
			SessionManager.SessionAPI.updateTitle(sessionId, title)
		
		override fun updateConfig(sessionId: UUID, config: SessionConfig) =
			SessionManager.SessionAPI.updateConfig(sessionId, config) ?: Unit
		
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
		override fun listAvailableProviderTypes() = ConfigManager.ProviderConfigAPI.ProviderAPI.listAvailable()
		
		override fun getProviderMeta(type: String): LlmClient.ProviderInfo =
			ConfigManager.ProviderConfigAPI.ProviderAPI.getMeta(type)
		
		override fun addProvider(provider: CoreConfig.ProviderConfig.Provider) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.create(provider)
		
		override fun removeProvider(name: String) = ConfigManager.ProviderConfigAPI.ProviderAPI.delete(name)
		
		override fun renameProvider(name: String, new: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.rename(name, new)
		
		override fun setProviderType(name: String, type: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateType(name, type)
		
		override fun setProviderKey(name: String, keyName: String) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateKey(name, keyName)
		
		override fun setProviderUrl(name: String, url: Url) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateUrl(name, url)
		
		override fun setProviderRule(name: String, rules: List<ProviderData.ErrorHandlingRule>) =
			ConfigManager.ProviderConfigAPI.ProviderAPI.updateRule(name, rules)
		
		override fun listModels() = ConfigManager.ProviderConfigAPI.ModelAPI.list()
		
		override fun listModelIds() = ConfigManager.ProviderConfigAPI.ModelAPI.listId()
		override fun getModelMeta(provider: String, modelId: String): ProviderData.ModelData.ModelInfo? =
			ConfigManager.ProviderConfigAPI.ModelAPI.getMeta(provider, modelId)
		
		override fun addModel(model: CoreConfig.ProviderConfig.Model) =
			ConfigManager.ProviderConfigAPI.ModelAPI.add(model)
		
		override fun removeModel(id: ModelId) = ConfigManager.ProviderConfigAPI.ModelAPI.remove(id)
		
		override fun setModel(id: ModelId, model: CoreConfig.ProviderConfig.Model) =
			ConfigManager.ProviderConfigAPI.ModelAPI.update(id, model)
		
		override fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey) =
			ConfigManager.ProviderConfigAPI.ApiKeyAPI.add(key)
		
		override fun listApiKeyNames() = ConfigManager.ProviderConfigAPI.ApiKeyAPI.list()
		
		override fun removeApiKey(name: String) = ConfigManager.ProviderConfigAPI.ApiKeyAPI.delete(name)
	}
}
