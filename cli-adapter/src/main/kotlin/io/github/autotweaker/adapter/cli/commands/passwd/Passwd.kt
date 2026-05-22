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
import io.github.autotweaker.adapter.cli.Command
import io.github.autotweaker.adapter.cli.Param
import io.github.autotweaker.adapter.cli.Request
import io.github.autotweaker.adapter.cli.Syntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

@AutoService(Command::class)
class Passwd : Command {
	private val logger = LoggerFactory.getLogger(this::class.java)
	override val name = "passwd"
	override val description get() = i18n.get(PasswdI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.leaf(Param.Flag("unlock", i18n.get(PasswdI18n.ParamUnlock()))),
			Syntax.leaf(Param.Flag("remove", i18n.get(PasswdI18n.ParamRemove()))),
			required = false,
		)
	private lateinit var core: CoreAPI
		private val i18n: I18nService get() = core.i18nService()
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request,
		prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<Command.Chunk> = flow {
		if (request.has("unlock")) {
			emitAll(handleUnlock(prompt))
			return@flow
		}
		
		if (request.has("remove")) {
			emitAll(handleRemove(prompt))
			return@flow
		}
		
		emitAll(handleChange(prompt))
	}
	
	private fun handleUnlock(prompt: suspend (text: String, echo: Boolean) -> String): Flow<Command.Chunk> = flow {
		if (core.isPasswordEmpty) {
			logger.debug("Unlock skipped  command=passwd  reason=no_password_set")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.UnlockNoPassword())))
			emit(Command.Chunk.Done())
			return@flow
		}
		
		if (core.isUnlocked) {
			logger.debug("Unlock skipped  command=passwd  reason=already_unlocked")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.UnlockAlready())))
			emit(Command.Chunk.Done())
			return@flow
		}
		
		val password = prompt(i18n.get(PasswdI18n.UnlockPrompt()), false).also { emit(Command.Chunk.Data("")) }
		
		try {
			core.unlock(password)
			logger.info("Keystore unlocked  command=passwd")
		} catch (_: Exception) {
			logger.warn("Failed to unlock keystore  command=passwd")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.UnlockFailed()), Command.Chunk.Channel.STDERR))
			emit(Command.Chunk.Done(1))
			return@flow
		}
		emit(Command.Chunk.Done())
	}
	
	private fun handleRemove(prompt: suspend (text: String, echo: Boolean) -> String): Flow<Command.Chunk> = flow {
		val password = prompt(i18n.get(PasswdI18n.UnlockPrompt()), false)
		emit(Command.Chunk.Data(""))
		try {
			if (!core.isUnlocked) {
				core.unlock(password)
			}
			core.changePassword(password, "")
			logger.info("Password removed  command=passwd")
		} catch (_: Exception) {
			logger.warn("Failed to remove password  command=passwd")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.Invalid()), Command.Chunk.Channel.STDERR))
			emit(Command.Chunk.Done(1))
			return@flow
		}
		emit(Command.Chunk.Done())
	}
	
	private fun handleChange(prompt: suspend (text: String, echo: Boolean) -> String): Flow<Command.Chunk> = flow {
		val oldPassword = if (core.isPasswordEmpty) {
			""
		} else {
			prompt(i18n.get(PasswdI18n.UnlockPrompt()), false).also { emit(Command.Chunk.Data("")) }
		}
		
		val newPassword = prompt(i18n.get(PasswdI18n.PromptNew()), false)
		emit(Command.Chunk.Data(" " + i18n.get(PasswdI18n.Length()).format(newPassword.length)))
		val confirm = prompt(i18n.get(PasswdI18n.PromptConfirm()), false)
		emit(Command.Chunk.Data(""))
		
		if (newPassword != confirm) {
			logger.debug("Password change aborted  command=passwd  reason=confirmation_mismatch")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.Mismatch()), Command.Chunk.Channel.STDERR))
			emit(Command.Chunk.Done(1))
			return@flow
		}
		
		try {
			if (!core.isUnlocked) {
				core.unlock(oldPassword)
			}
			core.changePassword(oldPassword, newPassword)
			logger.info("Password changed  command=passwd")
		} catch (_: Exception) {
			logger.warn("Failed to change password  command=passwd")
			emit(Command.Chunk.Data(i18n.get(PasswdI18n.Invalid()), Command.Chunk.Channel.STDERR))
			emit(Command.Chunk.Done(1))
			return@flow
		}
		emit(Command.Chunk.Done())
	}
}