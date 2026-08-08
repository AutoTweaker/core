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
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.secret.env.Env
import io.github.autotweaker.adapter.cli.commands.secret.key.Key
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.exception.PasswordInvalidException

@AutoService(Command::class)
class Secret : Command, Traceable {
	override val name = "secret"
	override val description = i18n(SecretI18n.Desc())
	override val children: List<Command> = listOf(Env(), Key())
	override val requiresKeystore = false
	override val syntax = buildSyntax(XOR) {
		flag("unlock", SecretI18n.ParamUnlock())
		all {
			flag("passwd", SecretI18n.ParamUnlock()) {
				aliases()
			}
			flag("reset", PasswdI18n.ParamRemove()) {
				required = false
				aliases()
			}
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("unlock") {
			handleUnlock(core)
		}
		
		handleFlag("passwd") {
			if (hasArg("reset")) {
				handleRemove(core)
			} else {
				handleChange(core)
			}
		}
		
		error(SecretI18n.InvalidArg())
	}
	
	private suspend fun Console.handleUnlock(core: CoreAPI) {
		if (!core.secret.isUnlocked.value) {
			val password = secret(SecretI18n.UnlockPrompt())
			
			trace.catching { core.secret.unlock(password) }
				.recoverException { _: PasswordInvalidException ->
					error(SecretI18n.InvalidPasswd())
				}.getOrThrow()
		} else if (core.secret.isPasswordEmpty()) {
			out(SecretI18n.UnlockNoPassword())
		} else {
			out(SecretI18n.UnlockAlready())
		}
	}
	
	private suspend fun Console.handleRemove(core: CoreAPI) {
		val password = secret(SecretI18n.UnlockPrompt())
		
		trace.catching {
			if (!core.secret.isUnlocked.value) core.secret.unlock(password)
			core.secret.changePassword(password, "")
		}.getOrElse {
			error(SecretI18n.InvalidPasswd())
		}
	}
	
	private suspend fun Console.handleChange(core: CoreAPI) {
		val oldPassword = if (core.secret.isUnlocked.value && core.secret.isPasswordEmpty()) ""
		else secret(SecretI18n.UnlockPrompt())
		
		if (!core.secret.isUnlocked.value)
			trace.catching { core.secret.unlock(oldPassword) }
				.recoverException { _: PasswordInvalidException ->
					error(SecretI18n.InvalidPasswd())
				}.getOrThrow()
		
		val newPassword = secret(PasswdI18n.PromptNew())
		if (oldPassword == newPassword) error(PasswdI18n.SameAsOld())
		
		val confirm = secret(PasswdI18n.PromptConfirm())
		if (newPassword != confirm) error(PasswdI18n.Mismatch())
		
		trace.catching {
			core.secret.changePassword(oldPassword, newPassword)
		}.recoverException { _: PasswordInvalidException ->
			error(SecretI18n.InvalidPasswd())
		}.getOrThrow()
	}
}
