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

package io.github.autotweaker.api.types.exception

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

internal object ExceptionI18n {
	@AutoService(I18nDef::class)
	class DuplicateWorkspaceIdException : I18nBase(
		zh("ID 为 '%s' 的工作区已存在"),
		en("Workspace with ID '%s' already exists"),
	)
	
	@AutoService(I18nDef::class)
	class DuplicateWorkspaceNameException : I18nBase(
		zh("名为 '%s' 的工作区已存在"),
		en("Workspace named '%s' already exists"),
	)
	
	@AutoService(I18nDef::class)
	class AgentNotFoundException : I18nBase(
		zh("找不到 Agent: '%s', 所属会话: '%s'"),
		en("Agent not found: '%s', session: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class ModelNotFoundException : I18nBase(
		zh("找不到模型: '%s'"),
		en("Model not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class ProviderNotFoundException : I18nBase(
		zh("找不到模型提供商: '%s'"),
		en("Provider not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class SessionNotFoundException : I18nBase(
		zh("找不到会话: '%s'"),
		en("Session not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class WorkspaceNotFoundException : I18nBase(
		zh("找不到工作区: '%s'"),
		en("Workspace not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class DefaultWorkspaceMutationException : I18nBase(
		zh("不能修改或删除默认工作区"),
		en("Cannot modify or delete the default workspace"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidWorkspacePathException : I18nBase(
		zh("工作区路径 '%s' 不是一个目录"),
		en("Workspace path '%s' is not a directory"),
	)
	
	@AutoService(I18nDef::class)
	class PasswordInvalidException : I18nBase(
		zh("密码错误"),
		en("Invalid password"),
	)
	
	@AutoService(I18nDef::class)
	class PathOutsideWorkspaceException : I18nBase(
		zh("路径 '%s' 未被挂载到容器内"),
		en("Path '%s' is not mounted into the container"),
	)
	
	@AutoService(I18nDef::class)
	class SecretStoreLockedException : I18nBase(
		zh("密钥库已锁定, 请先解锁密钥库"),
		en("Secret store is locked, please unlock it first"),
	)
	
	@AutoService(I18nDef::class)
	class DuplicateModelNameException : I18nBase(
		zh("名为 '%s' 的模型已存在"),
		en("Model named '%s' already exists"),
	)
	
	@AutoService(I18nDef::class)
	class DefaultModelDeletionException : I18nBase(
		zh("不能删除默认模型或包含默认模型的提供商"),
		en("Cannot delete the default model or a provider containing the default model"),
	)
	
	@AutoService(I18nDef::class)
	class DuplicateProviderNameException : I18nBase(
		zh("名为 '%s' 的提供商已存在"),
		en("Provider named '%s' already exists"),
	)
	
	@AutoService(I18nDef::class)
	class UnknownProviderTypeException : I18nBase(
		zh("未知的提供商类型: '%s'"),
		en("Unknown provider type: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class ApiKeyNotFoundException : I18nBase(
		zh("找不到提供商密钥: '%s'"),
		en("API key not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class ApiKeyInUseException : I18nBase(
		zh("提供商密钥 '%s' 正在使用, 无法删除"),
		en("API key '%s' is in use and cannot be deleted"),
	)
	
	@AutoService(I18nDef::class)
	class DuplicateApiKeyException : I18nBase(
		zh("名为 '%s' 的提供商密钥已存在"),
		en("API key named '%s' already exists"),
	)
	
	@AutoService(I18nDef::class)
	class SettingNotFoundException : I18nBase(
		zh("找不到设置项: '%s'"),
		en("Setting not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class SettingTypeMismatchException : I18nBase(
		zh("设置项 '%s' 的格式不匹配, 应为: '%s', 但得到 '%s'"),
		en("Type mismatch for setting '%s': expected '%s', got '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class ToolNotFoundException : I18nBase(
		zh("在缓存中找不到名为 '%s' 的工具"),
		en("Tool named '%s' not found in cache"),
	)
	
	@AutoService(I18nDef::class)
	class ChatRetriesExhaustedException : I18nBase(
		zh("LLM 请求重试次数耗尽后仍未成功, 共计 %s 次尝试"),
		en("All LLM chat retries exhausted without success, total %s attempts"),
	)
	
	@AutoService(I18nDef::class)
	class I18nEntryNotFoundException : I18nBase(
		zh("找不到 i18n 条目: '%s'"),
		en("I18n entry not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class SecretNotFoundException : I18nBase(
		zh("找不到加密条目: '%s'"),
		en("Secret not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class AdapterNotFoundException : I18nBase(
		zh("找不到适配器: '%s'"),
		en("Adapter not found: '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class GpgException : I18nBase(
		zh("GPG 命令 '%s' 执行失败: %s"),
		en("GPG command '%s' failed: %s"),
	)
	
	@AutoService(I18nDef::class)
	class WorkspaceNotEmptyException : I18nBase(
		zh("工作区 '%s' 中包含会话, 无法删除"),
		en("Workspace '%s' contains sessions and cannot be deleted"),
	)
}
