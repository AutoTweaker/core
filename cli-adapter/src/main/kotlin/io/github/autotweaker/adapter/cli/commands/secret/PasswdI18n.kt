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
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object PasswdI18n {
	@AutoService(I18nDef::class)
	class ParamRemove : I18nBase(
		en("Remove password"),
		zh("移除密码"),
	)
	
	@AutoService(I18nDef::class)
	class PromptNew : I18nBase(
		en("New password:"),
		zh("新密码:"),
	)
	
	@AutoService(I18nDef::class)
	class PromptConfirm : I18nBase(
		en("Confirm new password:"),
		zh("确认新密码:"),
	)
	
	@AutoService(I18nDef::class)
	class Mismatch : I18nBase(
		en("Passwords do not match"),
		zh("两次密码不一致"),
	)
}
