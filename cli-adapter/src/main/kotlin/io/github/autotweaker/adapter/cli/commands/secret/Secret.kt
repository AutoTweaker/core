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
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

@AutoService(Command::class)
class Secret : Command {
	private val logger = LoggerFactory.getLogger(this::class.java)
	override val name = "secret"
	override val description get() = i18n.get(SecretI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.all(
				Syntax.leaf(Param.Flag("passwd", i18n.get(SecretI18n.ParamUnlock()), emptyList()), required = true),
				Syntax.leaf(Param.Flag("reset", i18n.get(PasswdI18n.ParamRemove()), emptyList())),
			),
			Syntax.leaf(Param.Flag("unlock", i18n.get(SecretI18n.ParamUnlock())), required = true),
			Syntax.all(
				Syntax.xor(
					Syntax.leaf(Param.Flag("list", i18n.get(SecretI18n.ParamList())), required = true),
					
					Syntax.leaf(Param.Value("add", i18n.get(SecretI18n.ParamAdd())), required = true), Syntax.leaf(
						Param.Value("remove", i18n.get(SecretI18n.ParamRemove()), aliases = listOf("rm")),
						required = true
					), Syntax.leaf(Param.Value("get", i18n.get(SecretI18n.ParamGet())), required = true)
				),
				Syntax.xor(
					Syntax.leaf(
						Param.Flag("key", i18n.get(SecretI18n.ParamKey())), required = true
					),
					Syntax.all(
						Syntax.leaf(
							Param.Flag("env", i18n.get(SecretI18n.ParamEnv())), required = true
						),
						Syntax.leaf(
							Param.Value("type", i18n.get(SecretI18n.ParamEnvType())), required = true
						),
					),
				),
			),
		)
	private lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18n.i18nService
	
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
			emit(CmdOutput.Data(i18n.get(SecretI18n.InvalidArg()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
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
			logger.debug("Unlock skipped  command=secret  reason=no_password_set")
			emit(CmdOutput.Data(i18n.get(SecretI18n.UnlockNoPassword())))
			emit(CmdOutput.Done())
			return@flow
		}
		
		if (core.secret.isUnlocked.value) {
			logger.debug("Unlock skipped  command=secret  reason=already_unlocked")
			emit(CmdOutput.Data(i18n.get(SecretI18n.UnlockAlready())))
			emit(CmdOutput.Done())
			return@flow
		}
		
		val password = prompt(i18n.get(SecretI18n.UnlockPrompt()), false).also { emit(CmdOutput.Data("")) }
		
		try {
			core.secret.unlock(password)
			logger.info("Keystore unlocked  command=secret")
		} catch (_: Exception) {
			logger.warn("Failed to unlock keystore  command=secret")
			emit(CmdOutput.Data(i18n.get(SecretI18n.UnlockFailed()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
			return@flow
		}
		emit(CmdOutput.Done())
	}
	
	private fun handleRemove(prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> = flow {
		val password = prompt(i18n.get(SecretI18n.UnlockPrompt()), false)
		emit(CmdOutput.Data(""))
		try {
			if (!core.secret.isUnlocked.value) {
				core.secret.unlock(password)
			}
			core.secret.changePassword(password, "")
			logger.info("Password removed  command=secret")
		} catch (_: Exception) {
			logger.warn("Failed to remove password  command=secret")
			emit(CmdOutput.Data(i18n.get(SecretI18n.InvalidPasswd()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
			return@flow
		}
		emit(CmdOutput.Done())
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
			logger.debug("Password change aborted  command=secret  reason=confirmation_mismatch")
			emit(CmdOutput.Data(i18n.get(PasswdI18n.Mismatch()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
			return@flow
		}
		
		try {
			if (!core.secret.isUnlocked.value) {
				core.secret.unlock(oldPassword)
			}
			core.secret.changePassword(oldPassword, newPassword)
			logger.info("Password changed  command=secret")
		} catch (_: Exception) {
			logger.warn("Failed to change password  command=secret")
			emit(CmdOutput.Data(i18n.get(SecretI18n.InvalidPasswd()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
			return@flow
		}
		emit(CmdOutput.Done())
	}
}
