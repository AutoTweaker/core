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

import com.google.common.collect.ImmutableBiMap
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.*
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.config.EnvType
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.exception.*
import io.github.autotweaker.api.types.exception.duplicate.DuplicateApiKeyException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateModelNameException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateProviderNameException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateWorkspaceNameException
import io.github.autotweaker.api.types.exception.notfound.*
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellExec
import io.github.autotweaker.api.types.tool.ToolMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Instant

/**
 * AutoTweaker/core 为适配器提供的 API，主要包含适配器、会话、配置的管理。
 *
 * 基础设施类的 API 通过 able 接口暴露，参见 [io.github.autotweaker.api.Loggable]、[io.github.autotweaker.api.Traceable]、[io.github.autotweaker.api.JsonStorable]、[io.github.autotweaker.api.I18nable]。
 *
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
	
	val adapter: AdapterAPI
	val session: SessionAPI
	val workspace: WorkspaceAPI
	val tool: ToolAPI
	val config: ConfigAPI
	val persistence: PersistenceAPI
	val secret: SecretAPI
	val i18n: I18nAPI
	val trace: TraceAPI
	val log: LogAPI
	
	/**
	 * 调用 LLM，模型、提供商必须来自配置的模型和提供商。
	 *
	 * 请求结束后必然返回一次 [ChatResult.Assembled] 或 [ChatResult.Failed]。由于重试机制，即使请求仍未结束也有可能在中途返回 [ChatResult.Failed]。
	 *
	 * @throws ModelNotFoundException
	 * @throws ProviderNotFoundException
	 * @throws SecretStoreLockedException
	 * @throws SecretNotFoundException
	 * @throws ChatRetriesExhaustedException
	 * @throws UnknownProviderTypeException
	 */
	fun chat(request: LlmRequest): Flow<LlmResult>
	
	/**
	 * 执行一条 Bash 命令，支持在容器内执行。
	 *
	 * @param arg 命令是整条 [String]，小心注入。
	 * @return 命令的实时输出，命令执行完毕后返回 [ShellEvent.Exit]。
	 * @throws SecretStoreLockedException
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
		 * @return 成功启动适配器返回 true，适配器正在运行返回 false。
		 * @throws AdapterNotFoundException
		 */
		suspend fun start(name: KebabCase): Boolean
		
		/**
		 * 根据适配器的 name 获取适配器是否正在运行。
		 *
		 * @throws AdapterNotFoundException
		 */
		suspend fun alive(name: KebabCase): Boolean
		
		/**
		 * 根据适配器的 name 停止适配器，AutoTweaker 不会捕获适配器在此过程中抛出的异常，请自行处理。
		 *
		 * @return 成功停止适配器返回 true，适配器未在运行返回 false。
		 * @throws AdapterNotFoundException
		 */
		suspend fun stop(name: KebabCase): Boolean
	}
	
	/**
	 * 管理会话，AutoTweaker AI 功能的主要 API。
	 *
	 * @see AgentAPI
	 */
	interface SessionAPI {
		/**
		 * 在默认工作区创建新会话。
		 *
		 * @param model 用于 main Agent 的模型配置。
		 * @return 新会话的 id。
		 * @throws SecretStoreLockedException
		 * @throws WorkspaceNotFoundException
		 * @throws InvalidWorkspacePathException
		 * @throws ModelNotFoundException
		 * @throws ProviderNotFoundException
		 * @throws SecretNotFoundException
		 */
		suspend fun create(model: ModelConfig): UUID
		
		/**
		 * 在指定工作区内创建新会话。
		 *
		 * @param workspace 工作区的 id。
		 * @param model 用于 main Agent 的模型配置。
		 * @return 新会话的 id。
		 * @throws SecretStoreLockedException
		 * @throws WorkspaceNotFoundException
		 * @throws InvalidWorkspacePathException
		 * @throws ModelNotFoundException
		 * @throws ProviderNotFoundException
		 * @throws SecretNotFoundException
		 */
		suspend fun create(workspace: UUID, model: ModelConfig): UUID
		
		/**
		 * 删除一个会话，删除前会先 Stop 所有的 Agent。
		 *
		 * @return 找不到会话返回 false，删除成功返回 true。
		 */
		suspend fun delete(sessionId: UUID): Boolean
		
		/**
		 * 获取会话的控制器。
		 *
		 * @return 所有 Agent 的 API，[SessionData] 数据流。
		 * @throws SecretStoreLockedException
		 * @throws SessionNotFoundException
		 * @throws WorkspaceNotFoundException
		 * @throws InvalidWorkspacePathException
		 * @throws AgentNotFoundException
		 * @throws ModelNotFoundException
		 * @throws ProviderNotFoundException
		 * @throws SecretNotFoundException
		 */
		suspend fun getHandle(sessionId: UUID): SessionHandle
		
		/**
		 * 更新会话标题。
		 *
		 * @param function 接收旧标题，返回新标题，可能被多次调用，不应有副作用。
		 * @throws SecretStoreLockedException
		 * @throws SessionNotFoundException
		 * @throws WorkspaceNotFoundException
		 * @throws InvalidWorkspacePathException
		 * @throws AgentNotFoundException
		 * @throws ModelNotFoundException
		 * @throws ProviderNotFoundException
		 * @throws SecretNotFoundException
		 */
		suspend fun updateTitle(sessionId: UUID, function: (String?) -> String?)
		
		/**
		 * 容器的启停由 AutoTweaker 内部管理，按需自动启动，不需要在调用 api 前检查此值。
		 *
		 * @return AutoTweaker 的容器是否在运行。
		 */
		fun isContainerRunning(): Boolean
	}
	
	/**
	 * 用于管理工作区的 API。
	 */
	interface WorkspaceAPI {
		/**
		 * 默认工作区的 id，永不变化。
		 */
		val default: UUID
		
		/**
		 * 创建一个新的工作区。
		 *
		 * @return 新工作区的数据。
		 * @throws InvalidWorkspacePathException
		 * @throws DuplicateWorkspaceNameException
		 */
		suspend fun create(meta: WorkspaceMeta): WorkspaceData
		
		/**
		 * 重命名一个工作区。
		 *
		 * @throws DefaultWorkspaceMutationException
		 * @throws WorkspaceNotFoundException
		 * @throws DuplicateWorkspaceNameException
		 */
		suspend fun rename(id: UUID, newName: String)
		
		/**
		 * 删除工作区，请确保工作区内无会话。
		 *
		 * @return 找不到工作区返回 false，删除成功返回 true。
		 * @throws DefaultWorkspaceMutationException
		 * @throws WorkspaceNotEmptyException
		 */
		suspend fun delete(id: UUID): Boolean
		
		/**
		 * 获取工作区数据。
		 */
		suspend fun get(id: UUID): WorkspaceData?
		
		/**
		 * 获取全部已有工作区的数据。
		 */
		suspend fun list(): List<WorkspaceData>
	}
	
	/**
	 * 查询工具属性或处理工具参数。
	 */
	interface ToolAPI {
		/**
		 * 从内存中获取所有工具的属性，不会调用 [io.github.autotweaker.api.tool.Tool.meta]。
		 *
		 * 所有工具的属性都会被缓存，并仅在请求 LLM 前刷新。
		 */
		fun getMeta(): Map<String, ToolMeta>?
		
		/**
		 * 用于反序列化 [AgentMessage.Tool.Call.validatedArgs]，得到解析后的请求数据类。
		 *
		 * AutoTweaker 会从内存中寻找 [toolName] 对应的实例，若对应工具所属插件未被加载或工具数据从未被缓存，会抛出异常。
		 *
		 * @throws ToolNotFoundException 在内存缓存中找不到对应工具
		 * @throws SerializationException 反序列化错误，如果 [args] 来自 [AgentMessage.Tool.Call.validatedArgs]，通常不会触发，除非工具格式发生了变更，而 [AgentMessage.Tool.Call] 使用旧的格式
		 */
		fun deserializeArgs(toolName: String, args: JsonElement): ToolArgs
		
		/**
		 * 用于反序列化 [AgentMessage.Tool.Call.validatedArgs]，适用于已知工具参数类型，可以拿到序列化器时。
		 *
		 * @throws SerializationException 反序列化错误，如果 [args] 来自 [AgentMessage.Tool.Call.validatedArgs]，通常不会触发，除非工具格式发生了变更，而 [AgentMessage.Tool.Call] 使用旧的格式
		 */
		fun <T : ToolArgs> deserializeArgs(deserializer: KSerializer<T>, args: JsonElement): T
	}
	
	/**
	 * 管理环境变量、模型提供商、模型。
	 *
	 * @see SecretAPI
	 */
	interface ConfigAPI {
		/**
		 * 获取所有设置，此 api 通常用于向用户展示，要获取自己注册的设置项请使用 [io.github.autotweaker.api.get]。
		 *
		 * 所有设置项都拥有描述，但 [SettingEntry] 不会包含，请使用 [I18nAPI.getString] 获取国际化的描述。
		 */
		fun getAllSettings(): List<SettingEntry>
		
		/**
		 * 获取设置条目硬编码的默认值和默认描述，可用于向用户展示或快速重置。
		 */
		fun getSettingDef(id: String): SettingDef<*>?
		
		/**
		 * 更新一个设置项的值，要更新自己注册的设置项请使用 [io.github.autotweaker.api.config.SettingService.set]。
		 *
		 * @throws SettingNotFoundException
		 * @throws SettingTypeMismatchException
		 */
		suspend fun setSetting(id: String, value: SettingValue<*>)
		
		/**
		 * 添加一个环境变量，允许覆盖。
		 *
		 * 环境变量加密存储。
		 *
		 * @throws SecretStoreLockedException
		 * @throws GpgException
		 */
		suspend fun setEnv(type: EnvType, id: String, value: String)
		
		/**
		 * 删除一个环境变量。
		 *
		 * @return 找不到环境变量返回 false，删除成功返回 true。
		 * @throws SecretStoreLockedException
		 */
		suspend fun removeEnv(type: EnvType, id: String): Boolean
		
		/**
		 * 获取一个环境变量的值。
		 *
		 * @return 环境变量的 value，找不到返回 null。
		 * @throws SecretStoreLockedException
		 */
		suspend fun getEnv(type: EnvType, id: String): String?
		
		/**
		 * 列出指定类型的环境变量。
		 *
		 * @return 环境变量的 key 列表，不包含值。
		 */
		suspend fun listEnv(type: EnvType): List<String>
		
		/**
		 * 列出可用的提供商类型，即 [LlmClient] 注册的 `name`。
		 *
		 * @see LlmClient.ProviderInfo
		 */
		fun listAvailableProviderTypes(): Set<String>
		
		/**
		 * 获取指定类型提供商的元数据。可以用于展示或快速创建提供商配置。
		 *
		 * @throws UnknownProviderTypeException
		 */
		fun getProviderMeta(type: String): LlmClient.ProviderInfo
		
		/**
		 * 创建或更新一个提供商。
		 *
		 * @throws DuplicateProviderNameException
		 * @throws UnknownProviderTypeException
		 * @throws ApiKeyNotFoundException
		 */
		suspend fun setProvider(provider: ProviderData)
		
		/**
		 * 删除提供商，同时删除提供商的所有模型。
		 *
		 * @return 找不到返回 false，成功删除返回 true。
		 * @throws DefaultModelDeletionException 默认模型在此提供商下
		 * @see setDefaultModel
		 * @see getDefaultModel
		 */
		suspend fun removeProvider(id: UUID): Boolean
		
		/**
		 * 获取指定提供商的数据，找不到返回 null。
		 */
		suspend fun getProvider(id: UUID): ProviderData?
		
		/**
		 * 获取所有已配置提供商的数据。
		 */
		suspend fun listProviders(): List<ProviderData>
		
		/**
		 * 添加或更新一个模型。
		 *
		 * @throws ProviderNotFoundException
		 * @throws DuplicateModelNameException
		 */
		suspend fun setModel(model: ModelData)
		
		/**
		 * 删除一个模型配置。
		 *
		 * @throws DefaultModelDeletionException
		 * @see setDefaultModel
		 * @see getDefaultModel
		 */
		suspend fun removeModel(id: UUID): Boolean
		
		/**
		 * 获取一个模型的数据。
		 *
		 * @return 找不到模型返回 null
		 */
		suspend fun getModel(id: UUID): ModelData?
		
		/**
		 * 获取所有模型的数据。
		 */
		suspend fun listModels(): List<ModelData>
		
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
		 * @throws ModelNotFoundException
		 */
		suspend fun setDefaultModel(id: UUID?)
		
		/**
		 * 添加一个新的 api key。
		 *
		 * @throws DuplicateApiKeyException
		 * @throws SecretStoreLockedException
		 * @throws GpgException
		 */
		suspend fun addApiKey(name: String, key: String): UUID
		
		/**
		 * 删除一个 api key。
		 *
		 * @return 成功删除返回 true，找不到 key 返回 false。
		 * @throws ApiKeyInUseException
		 * @throws SecretStoreLockedException
		 */
		suspend fun removeApiKey(id: UUID): Boolean
		
		/**
		 * 删除一个 api key。
		 *
		 * @return 成功删除返回 true，找不到 key 返回 false。
		 * @throws ApiKeyInUseException
		 * @throws SecretStoreLockedException
		 */
		suspend fun removeApiKey(name: String): Boolean
		
		/**
		 * 列出所有 api key。
		 *
		 * @return K、V 分别为 id 和 displayName（不是值）
		 */
		suspend fun listApiKey(): ImmutableBiMap<UUID, String>
	}
	
	/**
	 * 读取持久化数据的 API。
	 *
	 * 其他关于持久化的内容分布在 [ConfigAPI] 和 [WorkspaceAPI] 等专用 API。
	 */
	interface PersistenceAPI {
		/**
		 * 从数据库加载会话数据。
		 *
		 * [SessionAPI.getHandle] 可能会触发会话的实例化，如果只是查数据，请使用此 api。
		 *
		 * @return 找不到会话返回 [emptyList]。
		 */
		suspend fun loadData(ids: Set<UUID>): List<SessionData>
		
		/**
		 * 从数据库加载 agent 数据，找不到返回 null。
		 *
		 * agent 数据中索引了上下文中所有消息的 id，请自行通过 [loadMessages] 加载，请按需加载。
		 */
		suspend fun loadAgent(id: UUID): AgentData?
		
		/**
		 * 从数据库加载会话消息，请按需加载。
		 *
		 * @return 找不到消息返回 [emptyList]。
		 */
		suspend fun loadMessages(ids: Set<UUID>): List<AgentMessage>
		
		/**
		 * 从数据库加载 Usage 数据，用于统计。
		 *
		 * @param before 加载比这更早的 Usage 数据，不包含 [before]。
		 * @see UsageEntry
		 */
		suspend fun loadUsage(limit: Int, before: UsageCursor?): List<UsageEntry>
		
		/**
		 * 从数据库加载 Usage 数据，用于统计。
		 *
		 * 适用于从 [io.github.autotweaker.api.types.agent.AgentContextIndex.ids] 加载 Usage 数据。
		 *
		 * @see UsageEntry
		 */
		suspend fun loadUsage(ids: Set<UUID>): List<UsageEntry>
		
		/**
		 * 从数据库获取合并后的 Usage 数据（[Usage.plus] 语义），无匹配项时返回 null。
		 *
		 * 三个字段均为 null 时返回历史的全部 Usage 数据之和。
		 *
		 * @param modelId 按模型过滤 Usage。
		 */
		suspend fun mergeUsage(modelId: UUID?, from: Instant?, to: Instant?): Usage?
		
		/**
		 * 从数据库获取合并后的 Usage 数据（[Usage.plus] 语义），无匹配项时返回 null。
		 *
		 * 适用于从 [io.github.autotweaker.api.types.agent.AgentContextIndex.ids] 统计 Usage 而无需加载消息内容。
		 */
		suspend fun mergeUsage(ids: Set<UUID>): Usage?
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
		 * 密钥库是否已经解锁，可订阅 flow 来等待密钥库解锁。
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
		 * @throws GpgException
		 * @see SecretAPI
		 */
		suspend fun unlock(password: String)
		
		/**
		 * 修改密码。在从用户接收密码时，请注意安全。
		 *
		 * @throws SecretStoreLockedException
		 * @throws PasswordInvalidException
		 * @throws GpgException
		 * @throws SecretNotFoundException
		 * @see SecretAPI
		 */
		suspend fun changePassword(oldPassword: String, newPassword: String)
	}
	
	/**
	 * 管理 i18n 服务和 i18n 自动翻译服务的 API。
	 *
	 * AutoTweaker 会合并设置项描述和 i18n 条目。
	 *
	 * AutoTweaker 会在后台对已注册的 i18n 条目进行自动翻译。
	 *
	 * 如果翻译模型未设置，翻译将不会进行。
	 *
	 * @see setTranslationModel
	 */
	interface I18nAPI {
		/**
		 * 获取某个 id 硬编码在声明中的 i18n 文案。
		 *
		 * 找不到返回 null。
		 */
		fun getDefault(id: String): Localizations?
		
		/**
		 * 通过 id 获取国际化的文本，通常用于获取设置项的描述。
		 *
		 * 不会抛出异常，条目不存在会返回 id 本身。
		 *
		 * 要获取自己注册的 i18n 请使用 [io.github.autotweaker.api.i18n.I18nService]。
		 *
		 * @param id 可以是 [SettingEntry.id]，也可以是 i18n 条目的 id。
		 */
		fun getString(id: String): String
		
		/**
		 * 获取所有 i18n 条目以及所有语言的文本，包括自动翻译后的文本。
		 *
		 * @see I18nEntries
		 */
		fun getAll(): I18nEntries
		
		/**
		 * 更新一个 i18n 条目某个语言的文本，或添加一个语言的文本。
		 *
		 * 不允许添加未注册的条目。
		 *
		 * @throws I18nEntryNotFoundException
		 */
		fun set(id: String, text: String, languageCode: Locale)
		
		/**
		 * 设置程序在国际化等场景下使用的语言。
		 */
		fun setLanguage(locale: Locale)
		
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
		suspend fun namespaces(origin: String): List<KebabCase>
		
		/**
		 * 列出指定记录者指定命名空间下的条目的时间戳。
		 */
		suspend fun entries(origin: String, namespace: KebabCase, range: UIntRange): List<Instant>
		
		/**
		 * 获取指定记录者指定命名空间下所有条目的总数。
		 */
		suspend fun count(origin: String, namespace: KebabCase): Int
		
		/**
		 * 获取指定记录者指定命名空间下指定时间戳的条目。trace 条目可能较大，请妥善处理。
		 */
		suspend fun get(origin: String, namespace: KebabCase, timestamp: Instant): String?
		
		/**
		 * 删除指定记录者指定命名空间下指定时间戳的条目。
		 *
		 * 无匹配条目返回 false。
		 */
		suspend fun remove(origin: String, namespace: KebabCase, timestamp: Instant): Boolean
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
