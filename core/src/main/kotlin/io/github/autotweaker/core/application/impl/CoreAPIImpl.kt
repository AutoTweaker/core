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

package io.github.autotweaker.core.application.impl

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.core.domain.port.ApiKeyRepository
import io.github.autotweaker.core.domain.port.EnvRepository
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.domain.session.UsageStore
import io.github.autotweaker.core.domain.session.WorkspaceAPI
import io.github.autotweaker.core.infrastructure.data.SecretManager
import io.github.autotweaker.core.infrastructure.i18n.I18nServiceImpl
import io.github.autotweaker.core.infrastructure.i18n.translation.TranslationManager
import io.github.autotweaker.core.infrastructure.persist.LogStore
import io.github.autotweaker.core.infrastructure.persist.db.config.Settings
import io.github.autotweaker.core.infrastructure.persist.db.trace.TraceStore
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.time.Instant

class CoreAPIImpl(
	private val envRepo: EnvRepository,
	private val providerRepo: ProviderRepository,
	private val modelRepo: ModelConfigRepository,
	private val apiKeyRepo: ApiKeyRepository,
	override val adapter: CoreAPI.AdapterAPI,
	override val pathResolver: PathResolver,
	override val appVersion: SemVer
) : CoreAPI {
	override val session = object : CoreAPI.SessionAPI {
		override val defaultWorkspaceId = WorkspaceManager.defaultWorkspaceId
		override suspend fun create(model: ModelConfig) = SessionManager.create(model)
		override suspend fun create(workspaceId: UUID, model: ModelConfig) =
			SessionManager.create(workspaceId, model)
		
		override suspend fun delete(sessionId: UUID) = SessionManager.delete(sessionId)
		
		override suspend fun getHandle(sessionId: UUID) = SessionManager.get(sessionId)
		override suspend fun updateTitle(sessionId: UUID, title: String) = SessionManager.updateTitle(sessionId, title)
		
		override suspend fun loadData(ids: List<UUID>) = SessionManager.loadData(ids)
		override suspend fun loadMessages(ids: List<UUID>) = SessionManager.loadMessages(ids)
		override suspend fun loadAgent(id: UUID) = SessionManager.loadAgent(id)
		override suspend fun getUsageSnapshots() = UsageStore.getSnapshots()
		
		override suspend fun createWorkspace(meta: WorkspaceMeta) = WorkspaceAPI.create(meta)
		override suspend fun renameWorkspace(id: UUID, newName: String) = WorkspaceAPI.rename(id, newName)
		override suspend fun deleteWorkspace(id: UUID) = WorkspaceAPI.delete(id)
		override suspend fun listWorkspaces() = WorkspaceAPI.list()
		override fun isContainerRunning(): Boolean = SessionManager.isContainerRunning()
	}
	
	override val config = object : CoreAPI.ConfigAPI {
		override suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type) = envRepo.list(type)
		override suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = envRepo.get(type, id)
		override suspend fun setEnv(env: CoreConfig.JsonConfig.Env) = envRepo.set(env)
		override suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String) = envRepo.remove(type, id)
		override suspend fun listProviders() = providerRepo.list()
		override fun listAvailableProviderTypes() = providerRepo.listAvailable()
		override fun getProviderMeta(type: String): LlmClient.ProviderInfo = providerRepo.getMeta(type)
		override suspend fun setProvider(provider: CoreConfig.ProviderConfig.Provider) = providerRepo.set(provider)
		override suspend fun removeProvider(id: UUID) = providerRepo.remove(id)
		override suspend fun getProvider(id: UUID) = providerRepo.get(id)
		override suspend fun setModel(model: CoreConfig.ProviderConfig.Model) = modelRepo.set(model)
		override suspend fun getModel(id: UUID) = modelRepo.get(id)
		override suspend fun listModels() = modelRepo.list()
		override suspend fun removeModel(id: UUID) {
			modelRepo.remove(id)
		}
		
		override fun getDefaultModel(): UUID? = ModelResolverImpl.getDefaultModel()
		override suspend fun setDefaultModel(id: UUID) = ModelResolverImpl.setDefaultModel(id)
		override suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey) = apiKeyRepo.add(key)
		override suspend fun listApiKey() = apiKeyRepo.list()
		override suspend fun removeApiKey(name: String) = apiKeyRepo.remove(name)
		override fun getAllSettings() = Settings.getAllEntries()
		override fun getSettingDef(id: String) = Settings.getDef(id)
		override suspend fun setSetting(id: String, value: SettingValue<*>) = Settings.setById(id, value)
	}
	
	override val secret = object : CoreAPI.SecretAPI {
		override val isUnlocked = SecretManager.isUnlocked
		override fun isPasswordEmpty() = SecretManager.isPasswordEmpty
		override suspend fun unlock(password: String) = SecretManager.unlock(password)
		override suspend fun changePassword(oldPassword: String, newPassword: String) =
			SecretManager.changePassword(oldPassword, newPassword)
	}
	
	override val i18n = object : CoreAPI.I18nAPI {
		override fun getDefault(id: String) = I18nServiceImpl.getDefault(id)
		override fun set(id: String, text: String, languageCode: Locale) = I18nServiceImpl.set(id, text, languageCode)
		override fun getAll() = I18nServiceImpl.getAllEntries()
		override fun getLanguage() = I18nServiceImpl.getLanguage()
		override fun setLanguage(locale: Locale) = I18nServiceImpl.setLanguage(locale)
		override fun getString(id: String) = I18nServiceImpl.resolveByKey(id)
		override suspend fun setTranslationModel(modelId: UUID?) = TranslationManager.setModel(modelId)
		override fun getTranslationModel(): UUID? = TranslationManager.getModel()
		override fun startTranslation() = TranslationManager.startTranslation()
		override fun getTranslationStatus(): StateFlow<TranslationStatus> = TranslationManager.status
	}
	
	override val trace = object : CoreAPI.TraceAPI {
		override suspend fun origins() = TraceStore.selectOrigins()
		override suspend fun namespaces(origin: String) = TraceStore.selectNamespaces(origin).map { it.toKebab() }
		override suspend fun count(origin: String, namespace: KebabCase) = TraceStore.count(origin, namespace.value)
		override suspend fun entries(origin: String, namespace: KebabCase, range: UIntRange) =
			TraceStore.selectEntries(origin, namespace.value, range)
		
		override suspend fun get(origin: String, namespace: KebabCase, timestamp: Instant) =
			TraceStore.select(origin, namespace.value, timestamp)
		
		override suspend fun remove(origin: String, namespace: KebabCase, timestamp: Instant) =
			TraceStore.delete(origin, namespace.value, timestamp)
	}
	
	override val log = object : CoreAPI.LogAPI {
		override val flow = LogBus.flow
		override fun readLogs(start: Instant, end: Instant) =
			LogStore.readLogs(start, end)
	}
	
	override fun chat(request: CoreLlmRequest): Flow<CoreLlmResult> = ChatService.chat(request)
	override fun bash(arg: ShellExec): Flow<ShellEvent> = ShellRouter.exec(arg)
}
