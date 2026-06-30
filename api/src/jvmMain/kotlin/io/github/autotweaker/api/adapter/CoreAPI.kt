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

import io.github.autotweaker.api.PairList
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.path.PathResolver
import io.github.autotweaker.api.types.KebabId
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.exception.PasswordInvalidException
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.UsageSnapshot
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

/**
 * AutoTweaker/core 提供的 API，主要包含适配器、会话、配置的管理。
 *
 * 基础设施类的 API 通过 able 接口暴露，参见 [io.github.autotweaker.api.Loggable]、[io.github.autotweaker.api.Traceable]、[io.github.autotweaker.api.JsonStorable]、[io.github.autotweaker.api.Settable]、[io.github.autotweaker.api.I18nable]。
 *
 * 请确保在调用任何 API 前检查 [SecretAPI.isUnlocked]，否则 [SecretStoreLockedException] 可能在任何地方抛出。
 *
 * @throws SecretStoreLockedException 密钥库未解锁（参见 [SecretAPI]）
 * @see Adapter
 * @author WhiteElephant-abc
 */
interface CoreAPI {
	/**
	 * AutoTweaker 应用版本号，正如类型名称，AutoTweaker 遵循 SemVer 规范。
	 *
	 * @see SemVer
	 */
	val appVersion: SemVer
	
	/**
	 * @see AdapterAPI
	 */
	val adapter: AdapterAPI
	
	/**
	 * @see SessionAPI
	 */
	val session: SessionAPI
	
	/**
	 * @see ConfigAPI
	 */
	val config: ConfigAPI
	
	/**
	 * @see SecretAPI
	 */
	val secret: SecretAPI
	
	/**
	 * @see I18nAPI
	 */
	val i18n: I18nAPI
	
	/**
	 * @see TraceAPI
	 */
	val trace: TraceAPI
	
	/**
	 * @see LogAPI
	 */
	val log: LogAPI
	
	/**
	 * 调用 LLM，模型、提供商必须来自配置的模型和提供商。
	 *
	 * @return LLM 返回的流式数据（若有），请求结束后必然返回一次 [ChatResult.Assembled]，无论成功与失败（除非抛出异常）。
	 */
	fun chat(request: CoreLlmRequest): Flow<CoreLlmResult>
	
	/**
	 * 执行一条 Bash 命令，支持在容器内执行。
	 *
	 * @param arg 命令是整条 [String]，小心注入。
	 * @return 命令的实时输出，命令执行完毕后返回 [ShellEvent.Exit]。
	 */
	fun bash(arg: ShellExec): Flow<ShellEvent>
	
	/**
	 * @see PathResolver
	 */
	val pathResolver: PathResolver
	
	/**
	 * 查询和启停 AutoTweaker 加载的适配器，AutoTweaker 不支持动态加载或卸载适配器。
	 *
	 * @see Adapter
	 */
	interface AdapterAPI {
		/**
		 * 列出 AutoTweaker 加载的全部适配器。
		 *
		 * @return [Boolean] 表示适配器是否正在运行。
		 */
		suspend fun list(): PairList<AdapterInfo, Boolean>
		
		/**
		 * 根据适配器的 name 启动适配器，AutoTweaker 不会捕获适配器在此过程中抛出的异常，请自行处理。
		 *
		 * @throws IllegalArgumentException 找不到适配器。
		 * @return 成功启动适配器返回 true，适配器正在运行返回 false。
		 */
		suspend fun start(name: KebabId): Boolean
		
		/**
		 * 根据适配器的 name 获取适配器是否正在运行。
		 *
		 * @throws IllegalArgumentException 找不到适配器。
		 */
		suspend fun alive(name: KebabId): Boolean
		
		/**
		 * 根据适配器的 name 停止适配器，AutoTweaker 不会捕获适配器在此过程中抛出的异常，请自行处理。
		 *
		 * @throws IllegalArgumentException 找不到适配器。
		 * @return 成功停止适配器返回 true，适配器未在运行返回 false。
		 */
		suspend fun stop(name: KebabId): Boolean
	}
	
	/**
	 * 管理会话、Agent和工作区，AutoTweaker AI 功能的主要 API。
	 */
	interface SessionAPI {
		/**
		 * 默认工作区的 id，永不变化。
		 */
		val defaultWorkspaceId: UUID
		
		/**
		 * 在默认工作区创建新会话。
		 *
		 * @param model 用于 main Agent 的模型配置。
		 * @return 新会话的 id。
		 */
		suspend fun create(model: ModelConfig): UUID
		
		/**
		 * 在指定工作区内创建新会话。
		 *
		 * @param workspaceId 工作区的 id，传 [defaultWorkspaceId] 不会炸。
		 * @param model 用于 main Agent 的模型配置。
		 * @return 新会话的 id。
		 */
		suspend fun create(workspaceId: UUID, model: ModelConfig): UUID
		
		/**
		 * 删除一个会话，删除前会先 Stop 所有的 Agent。
		 *
		 * @return 找不到会话返回 false，删除成功返回 true。
		 */
		suspend fun delete(sessionId: UUID): Boolean
		
		/**
		 * 获取会话的控制器。
		 *
		 * @throws IllegalStateException 找不到会话、找不到工作区、工作区目录不存在。
		 * @return 所有 Agent 的 API，[SessionData] 数据流。
		 */
		suspend fun getHandle(sessionId: UUID): SessionHandle
		
		/**
		 * 更新会话标题。
		 *
		 * @throws IllegalStateException 找不到会话、找不到工作区、工作区目录不存在
		 */
		suspend fun updateTitle(sessionId: UUID, title: String)
		
		/**
		 * 从数据库加载会话数据，找不到不会炸。
		 *
		 * @return 找不到会话返回 [emptyList]。
		 */
		suspend fun loadData(ids: List<UUID>): List<SessionData>
		
		/**
		 * 从数据库加载 agent 数据，找不到返回 null。
		 *
		 * agent 数据中索引了上下文中所有消息的 id，请自行通过 [loadMessages] 加载，请按需加载。
		 */
		suspend fun loadAgent(id: UUID): AgentData?
		
		/**
		 * 从数据库加载会话消息，请按需加载，找不到不会炸。
		 *
		 * @return 找不到消息返回 [emptyList]。
		 */
		suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>
		
		/**
		 * 获取历史的全部 Usage。
		 *
		 * @return key 为消息 id，value 为 [UsageSnapshot]，可以通过 [loadMessages] 反查对应消息。
		 * @see UsageSnapshot
		 */
		fun getUsageSnapshots(): Map<UUID, UsageSnapshot>
		
		/**
		 * 创建一个新的工作区。
		 *
		 * @throws IllegalArgumentException [WorkspaceMeta.displayName] 与已有工作区重复。
		 * @throws IllegalStateException [WorkspaceMeta.path] 不是一个目录。
		 * @return 新工作区的数据。
		 */
		suspend fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
		
		/**
		 * 重命名一个工作区。
		 *
		 * @throws IllegalStateException 找不到工作区，或找到工作区后工作区被删除，更新名称时找不到工作区
		 * @throws IllegalArgumentException 新的名称与已有工作区重复，或新的名称与当前相同。
		 */
		suspend fun renameWorkspace(id: UUID, newName: String)
		
		/**
		 * 删除工作区，删除前会先遍历 delete 所有 session。
		 *
		 * @throws IllegalArgumentException 试图删除默认工作区。
		 * @return 找不到工作区返回 false，删除成功返回 true。
		 */
		suspend fun deleteWorkspace(id: UUID): Boolean
		
		/**
		 * 获取全部已有工作区的数据。
		 */
		suspend fun listWorkspaces(): List<WorkspaceData>
		
		/**
		 * 容器的启停由 AutoTweaker 内部管理，按需自动启动，不需要在调用 api 前检查此值。
		 *
		 * @return AutoTweaker 的容器是否在运行。
		 */
		fun isContainerRunning(): Boolean
	}
	
	/**
	 * 管理环境变量、模型提供商、模型。
	 *
	 * @see SecretAPI
	 * @see io.github.autotweaker.api.Settable
	 */
	interface ConfigAPI {
		/**
		 * 添加一个环境变量，允许覆盖。
		 *
		 * 环境变量加密存储。
		 */
		suspend fun setEnv(env: CoreConfig.JsonConfig.Env)
		
		/**
		 * 删除一个环境变量。
		 *
		 * @return 找不到环境变量返回 false，删除成功返回 true。
		 */
		suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): Boolean
		
		/**
		 * 获取一个环境变量的值。
		 *
		 * @return 环境变量的 value，找不到返回 null
		 */
		suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
		
		/**
		 * 列出指定类型的环境变量。
		 *
		 * @return 环境变量的 key 列表，不包含值。
		 */
		suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
		
		/**
		 * 列出可用的提供商类型，即 [LlmClient] 注册的 `name`。
		 *
		 * @see LlmClient.ProviderInfo
		 */
		fun listAvailableProviderTypes(): List<String>
		
		/**
		 * 获取指定类型提供商的元数据。可以用于展示或快速创建提供商配置。
		 *
		 * @throws IllegalArgumentException 找不到类型为 [type] 的提供商。
		 */
		fun getProviderMeta(type: String): LlmClient.ProviderInfo
		
		/**
		 * 创建或更新一个提供商。
		 *
		 * @see CoreConfig.ProviderConfig.Provider
		 * @throws IllegalStateException 已经存在 displayName 相同的提供商、已经存在 id 相同的提供商、找不到指定的 api key。
		 * @throws IllegalArgumentException 找不到类型为 type 的提供商。
		 */
		suspend fun setProvider(provider: CoreConfig.ProviderConfig.Provider)
		
		/**
		 * 删除提供商，同时删除提供商的所有模型。
		 *
		 * @throws IllegalArgumentException 默认模型在此提供商下。
		 * @return 找不到返回 false，成功删除返回 true。
		 * @see setDefaultModel
		 * @see getDefaultModel
		 */
		suspend fun removeProvider(id: UUID): Boolean
		
		/**
		 * 获取指定提供商的数据，找不到返回 null。
		 */
		suspend fun getProvider(id: UUID): CoreConfig.ProviderConfig.Provider?
		
		/**
		 * 获取所有已配置提供商的数据。
		 *
		 * 密钥不存在时会给 `keyId` 填充 "unknown"。
		 */
		suspend fun listProviders(): List<CoreConfig.ProviderConfig.Provider>
		
		/**
		 * 添加或更新一个模型。
		 *
		 * @throws IllegalArgumentException 同一提供商下存在 displayName 相同的模型。
		 */
		suspend fun setModel(model: CoreConfig.ProviderConfig.Model)
		
		/**
		 * 删除一个模型配置。
		 *
		 * @throws IllegalStateException 试图删除默认模型。
		 * @see setDefaultModel
		 * @see getDefaultModel
		 */
		suspend fun removeModel(id: UUID)
		
		/**
		 * 获取一个模型的数据。
		 *
		 * @return 找不到模型返回 null
		 */
		suspend fun getModel(id: UUID): CoreConfig.ProviderConfig.Model?
		
		/**
		 * 获取所有模型的数据。
		 */
		suspend fun listModels(): List<CoreConfig.ProviderConfig.Model>
		
		/**
		 * 获取默认模型的 id，关于默认模型，参见 [setDefaultModel]。
		 *
		 * @return 未设置默认模型返回 null。
		 * @see setDefaultModel
		 */
		fun getDefaultModel(): UUID?
		
		/**
		 * 设置一个默认模型。
		 *
		 * 所谓默认模型，就是 AutoTweaker 在无法通过模型配置中的 id 找到模型时作为 fallback 的模型。
		 * 本质上就是一个“备用模型”。
		 *
		 * @throws IllegalArgumentException 找不到模型。
		 */
		suspend fun setDefaultModel(id: UUID)
		
		/**
		 * 添加一个新的 api key。
		 *
		 * @throws IllegalArgumentException 存在同名 api key。
		 */
		suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
		
		/**
		 * 删除一个 api key。
		 *
		 * 删除前请先检查提供商数据，确保没有提供商正在使用这个 api key。
		 *
		 * @throws IllegalStateException key 正在被一个或多个提供商使用。
		 * @return 成功删除返回 true，找不到 key 返回 false。
		 */
		suspend fun removeApiKey(name: String): Boolean
		
		/**
		 * 列出所有 api key 的名称（不是值）。
		 */
		suspend fun listApiKey(): List<String>
	}
	
	/**
	 * AutoTweaker 使用 GPG 加密存储密钥，私钥与密文放在一起，需要通过用户设置密码来保护密文。
	 *
	 * AutoTweaker 在运行时将密码明文存储在内存之中，面对无 root 且不在同一用户下的进程，密码是安全的。
	 * 同时，只要 AutoTweaker 不在运行或者 [isUnlocked] 为 false，密码就是绝对安全的——只存储在用户脑中。
	 *
	 * 如果没有密码（[isPasswordEmpty]），那么加密毫无作用，[SecretAPI] 专门用来设置密码和接收密码。
	 *
	 * 在从用户接收密码时，请注意安全，AutoTweaker 接受同用户或 root 下任何进程带来的威胁，但插件不应该扩大这个威胁。
	 * 如果不具备安全接收密码的能力，请不要向用户获取密码——将解密交给其他适配器。
	 */
	interface SecretAPI {
		/**
		 * 密钥库已经解锁，请确保在调用任何 API 前检查此值，否则 [SecretStoreLockedException] 可能在任何地方抛出。
		 *
		 * 理论上此值为 true 后就再也不可能重新为 false。
		 */
		val isUnlocked: StateFlow<Boolean>
		
		/**
		 * 如果为 true，密码未设置。密码未设置时密钥库会自动解锁。
		 *
		 * @return 密码是否为空。
		 * @throws SecretStoreLockedException
		 */
		fun isPasswordEmpty(): Boolean
		
		/**
		 * 解锁密钥库。在从用户接收密码时，请注意安全。
		 *
		 * @throws PasswordInvalidException
		 * @see SecretAPI
		 */
		suspend fun unlock(password: String)
		
		/**
		 * 修改密码。在从用户接收密码时，请注意安全。
		 *
		 * @throws PasswordInvalidException
		 * @throws SecretStoreLockedException
		 * @see SecretAPI
		 */
		suspend fun changePassword(oldPassword: String, newPassword: String)
	}
	
	/**
	 * 管理 i18n 自动翻译的 API。
	 *
	 * AutoTweaker 会在后台对已注册的 i18n 条目进行自动翻译。
	 *
	 * 如果翻译模型未设置，翻译将不会进行。
	 *
	 * @see setTranslationModel
	 */
	interface I18nAPI {
		/**
		 * 设置用于 i18n 自动翻译的大模型，请自行确认模型有效。
		 *
		 * 设置无效的模型 id 不会抛出异常，但翻译触发时会 fallback 到默认模型，如果默认模型也无效，会 fallback 到模型列表中的第一个模型。
		 */
		suspend fun setTranslationModel(modelId: UUID?)
		
		/**
		 * 获取用于 i18n 翻译的大模型，模型可能无效，请自行确认。
		 *
		 * @return 未设置返回 null
		 */
		fun getTranslationModel(): UUID?
		
		/**
		 * 开始翻译所有 i18n 条目，方法会启动后台协程并立即返回，不会挂起等待翻译完毕。
		 *
		 * @return false 如果并没有真的启动翻译，例如翻译正在进行、翻译模型未设置或当前语言的 i18n 已经齐全。
		 */
		fun startTranslation(): Boolean
		
		/**
		 * 获取自动翻译的实时状态。
		 */
		fun getTranslationStatus(): StateFlow<TranslationStatus>
	}
	
	/**
	 * trace 的管理 API，仅支持管理，包括读取和删除。
	 *
	 * @see io.github.autotweaker.api.trace.TraceRecorder
	 */
	interface TraceAPI {
		/**
		 * 列出所有记录者的 id，id 是记录者的 `KClass.java.name`。
		 */
		suspend fun origins(): List<String>
		
		/**
		 * 列出指定记录者的所有命名空间。
		 */
		suspend fun namespaces(origin: String): List<KebabId>
		
		/**
		 * 列出指定记录者指定命名空间下的条目的时间戳。
		 */
		suspend fun entries(origin: String, namespace: KebabId, range: UIntRange): List<Instant>
		
		/**
		 * 获取指定记录者指定命名空间下所有条目的总数。
		 */
		suspend fun count(origin: String, namespace: KebabId): Int
		
		/**
		 * 获取指定记录者指定命名空间下指定时间戳的条目。trace 条目可能较大，请妥善处理。
		 */
		suspend fun get(origin: String, namespace: KebabId, timestamp: Instant): String?
		
		/**
		 * 删除指定记录者指定命名空间下指定时间戳的条目。
		 *
		 * @return 是否成功删除了大于或等于 1 条数据，正常情况下也只会删除一条。
		 */
		suspend fun remove(origin: String, namespace: KebabId, timestamp: Instant): Boolean
	}
	
	/**
	 * 获取应用程序的日志，不仅限于 AutoTweaker/core，只要使用了 [org.slf4j] 的 API。
	 */
	interface LogAPI {
		/**
		 * 获取实时的日志流，`replay = 1000`。
		 */
		val flow: SharedFlow<LogEvent<ExceptionInfo.Live>>
		
		/**
		 * 通过时间戳从文件系统获取历史日志。
		 */
		fun readLogs(start: Instant, end: Instant): List<LogEvent<ExceptionInfo.Stored>>
	}
}
