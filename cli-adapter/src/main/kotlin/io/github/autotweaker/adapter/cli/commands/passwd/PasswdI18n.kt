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

package io.github.autotweaker.adapter.cli.commands.passwd

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object PasswdI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Manage password"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "管理密码"),
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
	class ParamRemove : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Remove password"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "移除密码"),
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
	class PromptNew : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "New password:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新密码:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptConfirm : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Confirm new password:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "确认新密码:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Length : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "length: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "长度: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Mismatch : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Passwords do not match"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "两次密码不一致"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Invalid : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Invalid password"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密码错误"),
		)
	}
}
