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
import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.getOrElse
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Secret : Command, Loggable, I18nable, Traceable {
	private lateinit var core: CoreAPI
	
	override val name = "secret"
	override val description get() = i18n.get(SecretI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.all(
				Syntax.leaf(Param.Type.FLAG, "passwd", SecretI18n.ParamUnlock(), aliases = emptyList()),
				Syntax.leaf(
					Param.Type.FLAG,
					"reset",
					PasswdI18n.ParamRemove(),
					required = false,
					aliases = emptyList()
				),
			),
			Syntax.leaf(Param.Type.FLAG, "unlock", SecretI18n.ParamUnlock()),
			Syntax.all(
				Syntax.xor(
					Syntax.leaf(Param.Type.FLAG, "list", SecretI18n.ParamList()),
					Syntax.leaf(Param.Type.VALUE, "add", SecretI18n.ParamAdd()),
					Syntax.leaf(Param.Type.VALUE, "remove", SecretI18n.ParamRemove(), aliases = listOf("rm")),
					Syntax.leaf(Param.Type.VALUE, "get", SecretI18n.ParamGet()),
				),
				Syntax.xor(
					Syntax.leaf(Param.Type.FLAG, "key", SecretI18n.ParamKey()),
					Syntax.all(
						Syntax.leaf(Param.Type.FLAG, "env", SecretI18n.ParamEnv()),
						Syntax.leaf(Param.Type.VALUE, "type", SecretI18n.ParamEnvType()),
					),
				),
			),
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		if (request.has("unlock")) {
			emitAll(handleUnlock(prompt))
			return@flow
		}
		
		if (request.has("passwd")) {
			if (request.has("reset")) {
				emitAll(handleRemove(prompt))
				return@flow
			} else {
				emitAll(handleChange(prompt))
				return@flow
			}
		}
		
		suspend fun emitInvalidArg() {
			emitI18n(SecretI18n.InvalidArg(), error = true)
			emitDone(1)
		}
		
		if (request.has("key")) {
			val km = KeyManager(core, prompt)
			if (request.has("list")) {
				emitAll(km.list())
				return@flow
			}
			if (request.has("add")) {
				val name = request.get("add") ?: run {
					emitInvalidArg()
					return@flow
				}
				emitAll(km.add(name))
				return@flow
			}
			if (request.has("remove")) {
				val name = request.get("remove") ?: run {
					emitInvalidArg()
					return@flow
				}
				emitAll(km.remove(name))
				return@flow
			}
			emitInvalidArg()
			return@flow
		}
		
		if (request.has("env") && request.has("type")) {
			val em = EnvManager(core, prompt)
			val type = when (request.get("type")) {
				"bash" -> EnvManager.EnvType.BASH
				"container" -> EnvManager.EnvType.CONTAINER
				else -> {
					emitInvalidArg()
					return@flow
				}
			}
			
			if (request.has("list")) {
				emitAll(em.list(type))
				return@flow
			}
			if (request.has("add")) {
				val name = request.get("add") ?: run {
					emitInvalidArg()
					return@flow
				}
				emitAll(em.add(type, name))
				return@flow
			}
			if (request.has("get")) {
				val name = request.get("get") ?: run {
					emitInvalidArg()
					return@flow
				}
				emitAll(em.get(type, name))
				return@flow
			}
			if (request.has("remove")) {
				val name = request.get("remove") ?: run {
					emitInvalidArg()
					return@flow
				}
				emitAll(em.remove(type, name))
				return@flow
			}
			emitInvalidArg()
			return@flow
		}
		
		emitInvalidArg()
		return@flow
	}
	
	private fun handleUnlock(prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> = flow {
		if (core.secret.isPasswordEmpty()) {
			this@Secret.log.debug("Skipped unlock  command=secret  reason=no_password_set")
			emitI18n(SecretI18n.UnlockNoPassword())
			emitDone(0)
			return@flow
		}
		
		if (core.secret.isUnlocked.value) {
			this@Secret.log.debug("Skipped unlock  command=secret  reason=already_unlocked")
			emitI18n(SecretI18n.UnlockAlready())
			emitDone(0)
			return@flow
		}
		
		val password = prompt(i18n.get(SecretI18n.UnlockPrompt()), false).also { emit(CmdOutput.Data("")) }
		
		trace.catching {
			core.secret.unlock(password)
			this@Secret.log.info("Unlocked keystore  command=secret")
		}.getOrElse {
			this@Secret.log.warn("Failed keystore unlock  command=secret")
			emitI18n(SecretI18n.UnlockFailed(), error = true)
			emitDone(1)
			return@flow
		}
		emitDone()
	}
	
	private fun handleRemove(prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> = flow {
		val password = prompt(i18n.get(SecretI18n.UnlockPrompt()), false)
		emit(CmdOutput.Data(""))
		trace.catching {
			if (!core.secret.isUnlocked.value) {
				core.secret.unlock(password)
			}
			core.secret.changePassword(password, "")
			this@Secret.log.info("Removed password  command=secret")
		}.getOrElse {
			this@Secret.log.warn("Failed password removal  command=secret")
			emitI18n(SecretI18n.InvalidPasswd(), error = true)
			emitDone(1)
			return@flow
		}
		emitDone()
	}
	
	private fun handleChange(prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> = flow {
		val oldPassword = if (core.secret.isPasswordEmpty()) {
			""
		} else {
			prompt(i18n.get(SecretI18n.UnlockPrompt()), false).also { emit(CmdOutput.Data("")) }
		}
		
		val newPassword = prompt(i18n.get(PasswdI18n.PromptNew()), false)
		emit(CmdOutput.Data(" " + i18n.get(PasswdI18n.Length()).format(newPassword.length)))
		val confirm = prompt(i18n.get(PasswdI18n.PromptConfirm()), false)
		emit(CmdOutput.Data(""))
		
		if (newPassword != confirm) {
			this@Secret.log.debug("Aborted password change  command=secret  reason=confirmation_mismatch")
			emitI18n(PasswdI18n.Mismatch(), error = true)
			emitDone(1)
			return@flow
		}
		
		trace.catching {
			if (!core.secret.isUnlocked.value) {
				core.secret.unlock(oldPassword)
			}
			core.secret.changePassword(oldPassword, newPassword)
			this@Secret.log.info("Changed password  command=secret")
		}.getOrElse {
			this@Secret.log.warn("Failed password change  command=secret")
			emitI18n(SecretI18n.InvalidPasswd(), error = true)
			emitDone(1)
			return@flow
		}
		emitDone()
	}
}
