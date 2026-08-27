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

package io.github.autotweaker.core.domain.agent.chat

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object InjectionSettings {
	@AutoService(SettingDef::class)
	class SystemEnvironment : StringSetting(
		"""
			系统: %s
			主机名: %s
			用户名: %s
			发行版: %s
			内核版本: %s
			CPU架构: %s
			CPU核心: %s
			内存大小: %s
		""".trimIndent(),
		zh("注入到会话上下文中的系统状态模板")
	)
	
	
	@AutoService(SettingDef::class)
	class WorkspaceEnvironment : StringSetting(
		"""
			工作目录: %s
			是否为容器内: %s
			是否为 git 仓库: %s
			文件列表:
			%s
		""".trimIndent(),
		zh("注入到会话上下文中的工作区状态模板")
	)
	
	@AutoService(SettingDef::class)
	class GitEnvironment : StringSetting(
		"""
			HEAD: %s
			分支: %s
			远端: %s
			最近提交:
			%s
		""".trimIndent(),
		zh("注入到会话上下文中的工作区git状态模板")
	)
	
	@AutoService(SettingDef::class)
	class GitLogCount : IntSetting(
		20,
		zh("注入到会话上下文中的工作区git最近commit数量")
	)
}
