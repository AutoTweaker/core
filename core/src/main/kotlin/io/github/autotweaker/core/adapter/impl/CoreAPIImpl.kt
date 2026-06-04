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

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.core.adapter.i18n.I18nServiceImpl
import io.github.autotweaker.core.adapter.i18n.translation.TranslationManager
import io.github.autotweaker.core.application.ShellRouter
import io.github.autotweaker.core.application.chat.ChatService
import io.github.autotweaker.core.domain.port.ApiKeyRepository
import io.github.autotweaker.core.domain.port.EnvRepository
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.domain.session.UsageStore
import io.github.autotweaker.core.domain.session.WorkspaceAPI
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.persistence.ModelRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.ModelStore
import io.github.autotweaker.core.infrastructure.persistence.WorkspaceManager
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Instant

class CoreAPIImpl(
	private val adapterAPI: CoreAPI.AdapterAPI,
	private val envRepo: EnvRepository,
	private val providerRepo: ProviderRepository,
	private val modelRepo: ModelConfigRepository,
	private val apiKeyRepo: ApiKeyRepository,
) : CoreAPI {
	override val adapter = object : CoreAPI.AdapterAPI {
		override suspend fun listAdapter(): List<AdapterInfo> = adapterAPI.listAdapter()
		override suspend fun startAdapter(name: String) = adapterAPI.startAdapter(name)
		override suspend fun stopAdapter(name: String) = adapterAPI.stopAdapter(name)
	}
	
	override val session = object : CoreAPI.SessionAPI {
		override val defaultWorkspaceId = WorkspaceManager.DEFAULT_WORKSPACE_ID
		override suspend fun create(config: SessionConfig) = SessionManager.create(config)
		override suspend fun create(workspaceId: UUID, config: SessionConfig) =
			SessionManager.create(workspaceId, config)
		
		override suspend fun delete(sessionId: UUID) = SessionManager.delete(sessionId)
		override suspend fun send(sessionId: UUID, content: String, images: List<Base64>?) =
			SessionManager.send(sessionId, content, images)
		
		override suspend fun stop(sessionId: UUID) = SessionManager.stop(sessionId)
		override suspend fun pause(sessionId: UUID) = SessionManager.pauseAgent(sessionId)
		override suspend fun resume(sessionId: UUID) = SessionManager.resumeAgent(sessionId)
		override suspend fun cancel(sessionId: UUID) = SessionManager.cancelAgent(sessionId)
		override suspend fun retry(sessionId: UUID) = SessionManager.retryAgent(sessionId)
		override suspend fun compact(sessionId: UUID) = SessionManager.compactAgent(sessionId)
		override suspend fun approveToolCall(sessionId: UUID, approvals: List<ToolApprove>) =
			SessionManager.approveToolCall(sessionId, approvals)
		
		override suspend fun getHandle(sessionId: UUID) = SessionManager.get(sessionId)
		override suspend fun updateTitle(sessionId: UUID, title: String) = SessionManager.updateTitle(sessionId, title)
		override suspend fun updateConfig(sessionId: UUID, config: SessionConfig) =
			SessionManager.updateConfig(sessionId, config)
		
		override suspend fun loadData(ids: List<UUID>) = SessionManager.loadData(ids)
		override suspend fun loadContext(sessionId: UUID) = SessionManager.loadContext(sessionId)
		override suspend fun loadMessages(ids: List<UUID>) = SessionManager.loadMessages(ids)
		override fun getUsageSnapshots() = UsageStore.getSnapshots()
		override fun createWorkspace(meta: WorkspaceMeta) = WorkspaceAPI.create(meta)
		override fun renameWorkspace(id: UUID, newName: String) = WorkspaceAPI.rename(id, newName)
		override suspend fun deleteWorkspace(id: UUID) = WorkspaceAPI.delete(id)
		override fun listWorkspaces() = WorkspaceAPI.list()
		override fun isContainerRunning(): Boolean = SessionManager.isContainerRunning()
	}
	
	override val config = object : CoreAPI.ConfigAPI {
		override val settingService: SettingService = Settings
		override fun jsonStore(kClass: KClass<*>) = JsonStoreImpl.namespace(kClass)
		override suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type) = envRepo.list(type)
		override suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = envRepo.get(type, id)
		override suspend fun setEnv(env: List<CoreConfig.JsonConfig.Env>) = envRepo.set(env)
		override suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = envRepo.remove(type, id)
		override fun listProviders() = providerRepo.list()
		override fun listAvailableProviderTypes() = providerRepo.listAvailable()
		override fun getProviderMeta(type: String): LlmClient.ProviderInfo = providerRepo.getMeta(type)
		override fun addProvider(provider: CoreConfig.ProviderConfig.Provider) = providerRepo.create(provider)
		override fun removeProvider(id: UUID) = providerRepo.delete(id)
		override fun setProviderDisplayName(id: UUID, displayName: String) =
			providerRepo.updateDisplayName(id, displayName)
		
		override fun setProviderType(id: UUID, type: String) = providerRepo.updateType(id, type)
		override fun setProviderKey(id: UUID, keyName: String) = providerRepo.updateKey(id, keyName)
		override fun setProviderUrl(id: UUID, url: Url) = providerRepo.updateUrl(id, url)
		override fun setProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>) =
			providerRepo.updateRule(id, rules)
		
		override fun listModels() = modelRepo.list()
		override fun listModelIds(): List<UUID> = modelRepo.list().map { it.data.id }
		override fun getModelMeta(id: UUID): ModelData.ModelInfo? = ModelStore.get(id)?.modelInfo
		override fun addModel(model: CoreConfig.ProviderConfig.Model) = modelRepo.add(model)
		override fun removeModel(id: UUID) = modelRepo.remove(id)
		override fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model) = modelRepo.update(id, model)
		override fun getDefaultModel(): UUID? = ModelRepositoryImpl.getDefaultModel()
		override fun setDefaultModel(id: UUID) = ModelRepositoryImpl.setDefaultModel(id)
		override suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey) = apiKeyRepo.add(key)
		override fun listApiKeyNames() = apiKeyRepo.list()
		override fun removeApiKey(name: String) = apiKeyRepo.delete(name)
	}
	
	override val secret = object : CoreAPI.SecretAPI {
		override val isUnlocked = SecretManager.isUnlocked
		override fun isPasswordEmpty() = SecretManager.isPasswordEmpty
		override suspend fun unlock(password: String) = SecretManager.unlock(password)
		override suspend fun changePassword(oldPassword: String, newPassword: String) =
			SecretManager.changePassword(oldPassword, newPassword)
	}
	
	override val i18n = object : CoreAPI.I18nAPI {
		override val i18nService: I18nService = I18nServiceImpl
		override fun setTranslationModel(modelId: UUID?) = TranslationManager.setModel(modelId)
		override fun getTranslationModel(): UUID? = TranslationManager.getModel()
		override fun startTranslation() = TranslationManager.startTranslation()
		override fun getTranslationStatus(): StateFlow<TranslationStatus> = TranslationManager.status
	}
	
	override val trace = object : CoreAPI.TraceAPI {
		override suspend fun origins() = TraceStore.selectOrigins()
		override suspend fun namespaces(origin: String) = TraceStore.selectNamespaces(origin)
		override suspend fun entries(origin: String, namespace: String, range: UIntRange) =
			TraceStore.selectEntries(origin, namespace, range)
		
		override suspend fun get(origin: String, namespace: String, timestamp: Instant) =
			TraceStore.select(origin, namespace, timestamp)
		
		override suspend fun delete(origin: String, namespace: String, timestamp: Instant) =
			TraceStore.delete(origin, namespace, timestamp)
	}
	
	override fun trace(kClass: KClass<*>) = TraceRecorderImpl.recorder(kClass)
	override fun chat(request: CoreLlmRequest): Flow<CoreLlmResult> = ChatService.chat(request)
	override fun bash(arg: ShellExec): Flow<ShellEvent> = ShellRouter.exec(arg)
}
