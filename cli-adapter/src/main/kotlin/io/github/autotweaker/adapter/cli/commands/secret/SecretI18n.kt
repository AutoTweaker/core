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

package io.github.autotweaker.adapter.cli.commands.secret

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object SecretI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Manage secret"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理加密数据"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamUnlock : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Unlock the keystore"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "解锁密钥库"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamList : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出条目")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamAdd : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "要添加条目的名称")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamRemove : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "要删除条目名称")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamGet : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "要获取条目的名称，已保存的提供商密钥不能够获取")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamKey : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理提供商密钥")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamEnv : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理容器或暴露给大模型的环境变量")
		)
	}
	
	@AutoService(I18nDef::class)
	class ParamEnvType : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(
				Locale.SIMPLIFIED_CHINESE,
				"""指定环境变量的类型，可选值"container"表示容器内工作区所在容器的系统环境变量，可选值"bash"表示大模型运行命令可请求注入的环境变量"""
			)
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidArg : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Invalid arguments"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "无效的参数"),
		)
	}
	
	@AutoService(I18nDef::class)
	class UnlockAlready : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Keystore is already unlocked"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥库已解锁"),
		)
	}
	
	@AutoService(I18nDef::class)
	class UnlockNoPassword : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "No password set. Set a password first."),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "未设置密码，请先设置密码"),
		)
	}
	
	@AutoService(I18nDef::class)
	class UnlockPrompt : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Please enter password:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请输入密码:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class UnlockFailed : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Failed to unlock keystore"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥库解锁失败"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidPasswd : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Invalid password"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密码错误"),
		)
	}
	
	@AutoService(I18nDef::class)
	class EmptyNameError : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "条目名称不能为空")
		)
	}
	
	@AutoService(I18nDef::class)
	class EmptyKeyError : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥内容不能为空")
		)
	}
	
	@AutoService(I18nDef::class)
	class KeyExistsError : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "名为 '%s' 的密钥已存在")
		)
	}
	
	@AutoService(I18nDef::class)
	class KeyNotFoundError : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "不存在名为 '%s' 的密钥")
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptInputApiKey : I18nDef {
		override val localizations: List<LocalizedString> = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请输入密钥内容:")
		)
	}
}