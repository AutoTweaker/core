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

package io.github.autotweaker.api.adapter

import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.path.PathResolver
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.time.Instant

interface CoreAPI {
	val adapter: AdapterAPI
	val session: SessionAPI
	val config: ConfigAPI
	val secret: SecretAPI
	val i18n: I18nAPI
	val trace: TraceAPI
	val log: LogAPI
	
	val pathResolver: PathResolver
	
	fun chat(request: CoreLlmRequest): Flow<CoreLlmResult>
	fun bash(arg: ShellExec): Flow<ShellEvent>
	
	interface AdapterAPI {
		suspend fun list(): List<AdapterInfo>
		suspend fun start(name: String)
		suspend fun alive(name: String): Boolean
		suspend fun stop(name: String)
	}
	
	interface SessionAPI {
		val defaultWorkspaceId: UUID
		
		suspend fun create(model: ModelConfig): UUID
		suspend fun create(workspaceId: UUID, model: ModelConfig): UUID
		suspend fun delete(sessionId: UUID): Boolean
		suspend fun getHandle(sessionId: UUID): SessionHandle
		suspend fun updateTitle(sessionId: UUID, title: String)
		
		suspend fun loadData(ids: List<UUID>): List<SessionData>
		suspend fun loadAgent(id: UUID): AgentData?
		suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>
		
		suspend fun getUsageSnapshots(): Map<UUID, UsageSnapshot>
		
		suspend fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
		suspend fun renameWorkspace(id: UUID, newName: String)
		suspend fun deleteWorkspace(id: UUID): Boolean
		suspend fun listWorkspaces(): List<WorkspaceData>
		
		fun isContainerRunning(): Boolean
	}
	
	interface ConfigAPI {
		suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
		suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
		suspend fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
		suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)
		
		fun listProviders(): List<CoreConfig.ProviderConfig.Provider>
		fun listAvailableProviderTypes(): List<String>
		fun getProviderMeta(type: String): LlmClient.ProviderInfo
		fun addProvider(provider: CoreConfig.ProviderConfig.Provider)
		fun removeProvider(id: UUID)
		fun setProviderType(id: UUID, type: String)
		fun setProviderKey(id: UUID, keyName: String)
		fun setProviderUrl(id: UUID, url: Url)
		fun setProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
		fun setProviderDisplayName(id: UUID, displayName: String)
		
		fun listModels(): List<CoreConfig.ProviderConfig.Model>
		fun getModelMeta(id: UUID): ModelData.ModelInfo?
		fun addModel(model: CoreConfig.ProviderConfig.Model)
		fun removeModel(id: UUID)
		fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model)
		
		fun getDefaultModel(): UUID?
		fun setDefaultModel(id: UUID)
		
		suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
		suspend fun removeApiKey(name: String)
		fun listApiKeyNames(): List<String>
	}
	
	interface SecretAPI {
		val isUnlocked: StateFlow<Boolean>
		fun isPasswordEmpty(): Boolean
		
		suspend fun unlock(password: String)
		suspend fun changePassword(oldPassword: String, newPassword: String)
	}
	
	interface I18nAPI {
		fun setTranslationModel(modelId: UUID?)
		fun getTranslationModel(): UUID?
		
		fun startTranslation()
		fun getTranslationStatus(): StateFlow<TranslationStatus>
	}
	
	interface TraceAPI {
		suspend fun origins(): List<String>
		suspend fun namespaces(origin: String): List<String>
		suspend fun entries(origin: String, namespace: String, range: UIntRange): List<Instant>
		suspend fun count(origin: String, namespace: String): Int
		suspend fun get(origin: String, namespace: String, timestamp: Instant): String?
		suspend fun delete(origin: String, namespace: String, timestamp: Instant)
	}
	
	interface LogAPI {
		val flow: SharedFlow<LogEvent<ExceptionInfo.Live>>
		fun readLogs(start: Instant, end: Instant): List<LogEvent<ExceptionInfo.Stored>>
	}
}
