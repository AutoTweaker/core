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

internal object PasswdI18n {
	@AutoService(I18nDef::class)
	class ParamRemove : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Remove password"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "移除密码"),
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
}
