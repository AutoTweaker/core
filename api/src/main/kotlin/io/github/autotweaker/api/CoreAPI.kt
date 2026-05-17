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

package io.github.autotweaker.api

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.api.types.settings.SettingKey
import java.util.*

interface CoreAPI {
	val session: SessionAPI
	val config: ConfigAPI
	
	fun unlock(password: String)
	fun changePassword(oldPassword: String, newPassword: String)
	val isUnlocked: Boolean
	val isPasswordEmpty: Boolean
	
	fun listAdapter(): List<AdapterInfo>
	fun startAdapter(name: String)
	fun stopAdapter(name: String)
	
	fun jsonStore(namespace: String): JsonStore
	
	interface SessionAPI {
		suspend fun create(config: SessionConfig): SessionHandle
		suspend fun create(workspaceId: UUID, config: SessionConfig): SessionHandle
		suspend fun delete(sessionId: UUID)
		fun getHandle(sessionId: UUID): SessionHandle?
		fun updateTitle(sessionId: UUID, title: String)
		fun updateConfig(sessionId: UUID, config: SessionConfig)
		
		suspend fun stop(sessionId: UUID)
		fun pause(sessionId: UUID)
		fun resume(sessionId: UUID)
		fun cancel(sessionId: UUID)
		fun retry(sessionId: UUID)
		fun compact(sessionId: UUID)
		
		suspend fun send(sessionId: UUID, content: String, images: List<Base64>? = null)
		fun approveToolCall(sessionId: UUID, approvals: List<ToolApprove>)
		
		suspend fun loadData(ids: List<UUID>): List<SessionData>?
		suspend fun loadContext(sessionId: UUID): SessionContext?
		suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>?
		
		fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
		suspend fun renameWorkspace(id: UUID, newName: String)
		suspend fun deleteWorkspace(id: UUID)
		fun listWorkspaces(): List<WorkspaceData>
	}
	
	interface ConfigAPI {
		fun getAppConfig(key: SettingKey): CoreConfig.AppConfig?
		fun setAppConfig(config: CoreConfig.AppConfig)
		fun getAllAppConfigs(): List<CoreConfig.AppConfig>
		
		fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
		fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
		fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
		fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)
		
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
		fun listModelIds(): List<UUID>
		fun getModelMeta(id: UUID): ModelData.ModelInfo?
		fun addModel(model: CoreConfig.ProviderConfig.Model)
		fun removeModel(id: UUID)
		fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model)
		
		fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
		fun removeApiKey(name: String)
		fun listApiKeyNames(): List<String>
	}
}