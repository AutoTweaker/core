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
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.config.EnvType
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.domain.session.SessionManager
import io.github.autotweaker.core.domain.session.WorkspaceAPI
import io.github.autotweaker.core.infrastructure.config.ApiKeyRepository
import io.github.autotweaker.core.infrastructure.config.EnvRepository
import io.github.autotweaker.core.infrastructure.config.ModelConfigRepository
import io.github.autotweaker.core.infrastructure.config.ProviderRepository
import io.github.autotweaker.core.infrastructure.container.ContainerManager
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

class CoreAPIImpl(
	private val usageRepository: UsageRepository,
	private val sessionManager: SessionManager,
	private val containerManager: ContainerManager,
	private val envRepository: EnvRepository,
	private val providerRepository: ProviderRepository,
	private val modelConfigRepository: ModelConfigRepository,
	private val modelResolverImpl: ModelResolverImpl,
	private val apiKeyRepository: ApiKeyRepository,
	private val settings: Settings,
	private val translationManager: TranslationManager,
	private val chatService: ChatService,
	private val traceStore: TraceStore,
	private val shellRouter: ShellRouter,
	override val adapter: CoreAPI.AdapterAPI,
	override val pathResolver: PathResolver,
	override val appVersion: SemVer
) : CoreAPI {
	override val session = object : CoreAPI.SessionAPI {
		override suspend fun create(model: ModelConfig) = sessionManager.create(model)
		override suspend fun create(workspace: UUID, model: ModelConfig) =
			sessionManager.create(workspace, model)
		
		override suspend fun delete(sessionId: UUID) = sessionManager.delete(sessionId)
		override suspend fun getHandle(sessionId: UUID) = sessionManager.get(sessionId)
		override suspend fun updateTitle(sessionId: UUID, function: (String?) -> String?) =
			sessionManager.updateTitle(sessionId, function)
		
		override fun isContainerRunning(): Boolean = containerManager.isRunning
	}
	
	override val workspace = object : CoreAPI.WorkspaceAPI {
		override val default = WorkspaceManager.defaultWorkspaceId
		override suspend fun create(meta: WorkspaceMeta) = WorkspaceAPI.create(meta)
		override suspend fun rename(id: UUID, newName: String) = WorkspaceAPI.rename(id, newName)
		override suspend fun delete(id: UUID) = WorkspaceAPI.delete(id)
		override suspend fun get(id: UUID) = WorkspaceAPI.get(id)
		override suspend fun list() = WorkspaceAPI.list()
	}
	
	override val tool = object : CoreAPI.ToolAPI {
		override fun getMeta(): Map<String, ToolMeta>? = Tools.getMetaCache()
		override fun deserializeArgs(toolName: String, args: JsonElement): ToolArgs =
			Tools.deserializeValidatedArgs(toolName, args)
		
		override fun <T : ToolArgs> deserializeArgs(deserializer: KSerializer<T>, args: JsonElement): T =
			Tools.deserializeValidatedArgs(deserializer, args)
	}
	
	override val config = object : CoreAPI.ConfigAPI {
		override suspend fun listEnv(type: EnvType) = envRepository.list(type)
		override suspend fun getEnv(type: EnvType, id: String) = envRepository.get(type, id)
		override suspend fun setEnv(type: EnvType, id: String, value: String) = envRepository.set(type, id, value)
		override suspend fun removeEnv(type: EnvType, id: String) = envRepository.remove(type, id)
		override suspend fun listProviders() = providerRepository.list()
		override fun listAvailableProviderTypes() = providerRepository.listAvailable()
		override fun getProviderMeta(type: String): LlmClient.ProviderInfo = providerRepository.getMeta(type)
		override suspend fun setProvider(provider: ProviderData) = providerRepository.set(provider)
		override suspend fun removeProvider(id: UUID) = providerRepository.remove(id)
		override suspend fun getProvider(id: UUID) = providerRepository.get(id)
		override suspend fun setModel(model: ModelData) = modelConfigRepository.set(model)
		override suspend fun getModel(id: UUID) = modelConfigRepository.get(id)
		override suspend fun listModels() = modelConfigRepository.list()
		override suspend fun removeModel(id: UUID) = modelConfigRepository.remove(id)
		
		override fun getDefaultModel(): UUID? = modelResolverImpl.getDefaultModel()
		override suspend fun setDefaultModel(id: UUID?) = modelResolverImpl.setDefaultModel(id)
		override suspend fun addApiKey(name: String, key: String) = apiKeyRepository.add(name, key)
		override suspend fun listApiKey() = apiKeyRepository.list()
		override suspend fun removeApiKey(id: UUID) = apiKeyRepository.remove(id)
		override suspend fun removeApiKey(name: String) = apiKeyRepository.remove(name)
		override fun getAllSettings() = settings.getAllEntries()
		override fun getSettingDef(id: String) = settings.getDef(id)
		override suspend fun setSetting(id: String, value: SettingValue<*>) = settings.set(id, value)
	}
	
	override val persistence = object : CoreAPI.PersistenceAPI {
		override suspend fun loadData(ids: Set<UUID>) = sessionManager.loadData(ids)
		override suspend fun loadMessages(ids: Set<UUID>) = sessionManager.loadMessages(ids)
		override suspend fun loadAgent(id: UUID) = sessionManager.loadAgent(id)
		override suspend fun loadUsage(ids: Set<UUID>) = usageRepository.load(ids)
		override suspend fun loadUsage(limit: Int, before: UsageCursor?) = usageRepository.load(limit, before)
		override suspend fun mergeUsage(ids: Set<UUID>) = usageRepository.summarize(ids)
		override suspend fun mergeUsage(modelId: UUID?, from: Instant?, to: Instant?) =
			usageRepository.summarize(modelId, from, to)
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
		override fun setLanguage(locale: Locale) = I18nServiceImpl.setLanguage(locale)
		override fun getString(id: String) = I18nServiceImpl.resolveByKey(id)
		override suspend fun setTranslationModel(modelId: UUID?) = translationManager.setModel(modelId)
		override fun getTranslationModel(): UUID? = translationManager.getModel()
		override fun startTranslation() = translationManager.startTranslation()
		override fun getTranslationStatus(): StateFlow<TranslationStatus> = translationManager.status
	}
	
	override val trace = object : CoreAPI.TraceAPI {
		override suspend fun origins() = traceStore.selectOrigins()
		override suspend fun namespaces(origin: String) = traceStore.selectNamespaces(origin).map { it.toKebab() }
		override suspend fun count(origin: String, namespace: KebabCase) = traceStore.count(origin, namespace.value)
		override suspend fun entries(origin: String, namespace: KebabCase, range: UIntRange) =
			traceStore.selectEntries(origin, namespace.value, range)
		
		override suspend fun get(origin: String, namespace: KebabCase, timestamp: Instant) =
			traceStore.select(origin, namespace.value, timestamp)
		
		override suspend fun remove(origin: String, namespace: KebabCase, timestamp: Instant) =
			traceStore.delete(origin, namespace.value, timestamp)
	}
	
	override val log = object : CoreAPI.LogAPI {
		override val flow = LogBus.flow
		override fun readLogs(start: Instant, end: Instant) =
			LogStore.readLogs(start, end)
	}
	
	override fun chat(request: LlmRequest): Flow<LlmResult> = chatService.chat(request)
	override fun bash(arg: ShellExec): Flow<ShellEvent> = shellRouter.exec(arg)
}
