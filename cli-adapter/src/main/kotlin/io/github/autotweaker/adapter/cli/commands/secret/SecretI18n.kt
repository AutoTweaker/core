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
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object SecretI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		Locale.ENGLISH to "Manage secret",
		Locale.SIMPLIFIED_CHINESE to "管理加密数据",
	)
	
	@AutoService(I18nDef::class)
	class ParamUnlock : I18nBase(
		Locale.ENGLISH to "Unlock the keystore",
		Locale.SIMPLIFIED_CHINESE to "解锁密钥库",
	)
	
	@AutoService(I18nDef::class)
	class ParamList : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "列出条目"
	)
	
	@AutoService(I18nDef::class)
	class ParamAdd : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "要添加条目的名称"
	)
	
	@AutoService(I18nDef::class)
	class ParamRemove : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "要删除条目名称"
	)
	
	@AutoService(I18nDef::class)
	class ParamGet : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "要获取条目的名称，已保存的提供商密钥不能够获取"
	)
	
	@AutoService(I18nDef::class)
	class ParamKey : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "管理提供商密钥"
	)
	
	@AutoService(I18nDef::class)
	class ParamEnv : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "管理容器或暴露给大模型的环境变量"
	)
	
	@AutoService(I18nDef::class)
	class ParamEnvType : I18nBase(
		Locale.SIMPLIFIED_CHINESE to """指定环境变量的类型，可选值"container"/"bash"""",
	)
	
	@AutoService(I18nDef::class)
	class InvalidArg : I18nBase(
		Locale.ENGLISH to "Invalid arguments",
		Locale.SIMPLIFIED_CHINESE to "无效的参数",
	)
	
	@AutoService(I18nDef::class)
	class UnlockAlready : I18nBase(
		Locale.ENGLISH to "Keystore is already unlocked",
		Locale.SIMPLIFIED_CHINESE to "密钥库已解锁",
	)
	
	@AutoService(I18nDef::class)
	class UnlockNoPassword : I18nBase(
		Locale.ENGLISH to "No password set. Set a password first.",
		Locale.SIMPLIFIED_CHINESE to "未设置密码，请先设置密码",
	)
	
	@AutoService(I18nDef::class)
	class UnlockPrompt : I18nBase(
		Locale.ENGLISH to "Please enter password:",
		Locale.SIMPLIFIED_CHINESE to "请输入密码:",
	)
	
	@AutoService(I18nDef::class)
	class UnlockFailed : I18nBase(
		Locale.ENGLISH to "Failed to unlock keystore",
		Locale.SIMPLIFIED_CHINESE to "密钥库解锁失败",
	)
	
	@AutoService(I18nDef::class)
	class InvalidPasswd : I18nBase(
		Locale.ENGLISH to "Invalid password",
		Locale.SIMPLIFIED_CHINESE to "密码错误",
	)
	
	@AutoService(I18nDef::class)
	class EmptyNameError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "条目名称不能为空"
	)
	
	@AutoService(I18nDef::class)
	class EmptyKeyError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "密钥内容不能为空"
	)
	
	@AutoService(I18nDef::class)
	class KeyExistsError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "名为 '%s' 的密钥已存在"
	)
	
	@AutoService(I18nDef::class)
	class KeyNotFoundError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "不存在名为 '%s' 的密钥"
	)
	
	@AutoService(I18nDef::class)
	class PromptInputApiKey : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "请输入密钥内容:"
	)
}
