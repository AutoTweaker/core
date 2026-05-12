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

package io.github.autotweaker.core.adapter.impl.cli.commands

import com.google.auto.service.AutoService
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Command.*
import io.github.autotweaker.core.adapter.impl.cli.ParsedRequest
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

@AutoService(Command::class)
class Passwd : Command {
	private val logger = LoggerFactory.getLogger(this::class.java)
	override val name = "passwd"
	override val description get() = I18n.get("cmd.passwd.desc")
	override val params
		get() = listOf(
			Param("u", I18n.get("passwd.param.unlock"), type = ParamType.FLAG_SHORT),
			Param("unlock", I18n.get("passwd.param.unlock"), type = ParamType.FLAG_LONG),
			Param("r", I18n.get("passwd.param.remove"), type = ParamType.FLAG_SHORT),
			Param("remove", I18n.get("passwd.param.remove"), type = ParamType.FLAG_LONG),
		)
	private lateinit var core: CoreAPI
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(request: ParsedRequest, prompt: suspend (String) -> String): Flow<Chunk> = flow {
		if (request.has("u") || request.has("unlock")) {
			emitAll(handleUnlock(prompt))
			return@flow
		}
		
		if (request.has("r") || request.has("remove")) {
			emitAll(handleRemove(prompt))
			return@flow
		}
		
		emitAll(handleChange(prompt))
	}
	
	private fun handleUnlock(prompt: suspend (String) -> String): Flow<Chunk> = flow {
		if (core.isPasswordEmpty) {
			logger.debug("Unlock skipped  command=passwd  reason=no_password_set")
			emit(Chunk.Data(I18n.get("unlock.no_password")))
			emit(Chunk.Done())
			return@flow
		}
		
		if (core.isUnlocked) {
			logger.debug("Unlock skipped  command=passwd  reason=already_unlocked")
			emit(Chunk.Data(I18n.get("unlock.already")))
			emit(Chunk.Done())
			return@flow
		}
		
		val password = prompt(I18n.get("unlock.prompt")).also { emit(Chunk.Data("")) }
		
		try {
			core.unlock(password)
			logger.info("Keystore unlocked  command=passwd")
		} catch (_: Exception) {
			logger.warn("Failed to unlock keystore  command=passwd")
			emit(Chunk.Data(I18n.get("unlock.failed"), Chunk.Channel.STDERR))
			emit(Chunk.Done(1))
			return@flow
		}
		emit(Chunk.Done())
	}
	
	private fun handleRemove(prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val password = prompt(I18n.get("unlock.prompt"))
		emit(Chunk.Data(""))
		try {
			if (!core.isUnlocked) {
				core.unlock(password)
			}
			core.changePassword(password, "")
			logger.info("Password removed  command=passwd")
		} catch (_: Exception) {
			logger.warn("Failed to remove password  command=passwd")
			emit(Chunk.Data(I18n.get("passwd.invalid"), Chunk.Channel.STDERR))
			emit(Chunk.Done(1))
			return@flow
		}
		emit(Chunk.Done())
	}
	
	private fun handleChange(prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val oldPassword = if (core.isPasswordEmpty) {
			""
		} else {
			prompt(I18n.get("unlock.prompt")).also { emit(Chunk.Data("")) }
		}
		
		val newPassword = prompt(I18n.get("passwd.prompt_new"))
		emit(Chunk.Data(" " + I18n.get("passwd.length", newPassword.length)))
		val confirm = prompt(I18n.get("passwd.prompt_confirm"))
		emit(Chunk.Data(""))
		
		if (newPassword != confirm) {
			logger.debug("Password change aborted  command=passwd  reason=confirmation_mismatch")
			emit(Chunk.Data(I18n.get("passwd.mismatch"), Chunk.Channel.STDERR))
			emit(Chunk.Done(1))
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
			emit(Chunk.Data(I18n.get("passwd.invalid"), Chunk.Channel.STDERR))
			emit(Chunk.Done(1))
			return@flow
		}
		emit(Chunk.Done())
	}
}
