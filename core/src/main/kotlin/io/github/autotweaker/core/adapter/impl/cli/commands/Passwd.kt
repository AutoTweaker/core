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

@AutoService(Command::class)
class Passwd : Command {
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
			emitAll(handleUnlock(request, prompt))
			return@flow
		}
		
		if (request.has("r") || request.has("remove")) {
			emitAll(handleRemove(prompt))
			return@flow
		}
		
		emitAll(handleChange(prompt))
	}
	
	private fun handleUnlock(
		request: ParsedRequest,
		prompt: suspend (String) -> String,
	): Flow<Chunk> = flow {
		if (core.isPasswordEmpty) {
			emit(Chunk.Data(I18n.get("unlock.no_password") + "\n"))
			return@flow
		}
		
		if (core.isUnlocked) {
			emit(Chunk.Data(I18n.get("unlock.already") + "\n"))
			return@flow
		}
		
		val password = if (request.stdin.isNotEmpty()) {
			request.stdin.trimEnd('\n')
		} else {
			prompt(I18n.get("unlock.prompt"))
		}
		
		try {
			core.unlock(password)
		} catch (_: Exception) {
			emit(Chunk.Data(I18n.get("unlock.failed") + "\n"))
		}
	}
	
	private fun handleRemove(prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val password = prompt(I18n.get("unlock.prompt"))
		try {
			if (!core.isUnlocked) {
				core.unlock(password)
			}
			core.changePassword(password, "")
		} catch (_: Exception) {
			emit(Chunk.Data(I18n.get("passwd.invalid") + "\n"))
		}
	}
	
	private fun handleChange(prompt: suspend (String) -> String): Flow<Chunk> = flow {
		val oldPassword = if (core.isPasswordEmpty) {
			""
		} else {
			prompt(I18n.get("unlock.prompt"))
		}
		
		if (!core.isPasswordEmpty) emit(Chunk.Data("\n"))
		val newPassword = prompt(I18n.get("passwd.prompt_new"))
		emit(Chunk.Data(" " + I18n.get("passwd.length", newPassword.length) + "\n"))
		val confirm = prompt(I18n.get("passwd.prompt_confirm"))
		
		if (newPassword != confirm) {
			emit(Chunk.Data(I18n.get("passwd.mismatch") + "\n"))
			return@flow
		}
		
		try {
			if (!core.isUnlocked) {
				core.unlock(oldPassword)
			}
			core.changePassword(oldPassword, newPassword)
		} catch (_: Exception) {
			emit(Chunk.Data(I18n.get("passwd.invalid") + "\n"))
		}
	}
}
