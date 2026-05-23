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
							Param.Value("type", i18n.get(SecretI18n.ParamEnvType())),
						),
					),
				),
			),
		)
	private lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18nService()
	
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
		
		if (request.has("key")) {
			val km = KeyManager(core, prompt)
			if (request.has("list")) {
				emitAll(km.list())
				return@flow
			}
			if (request.has("add")) {
				val name = request.get("add") ?: error("Missing key name")
				emitAll(km.add(name))
				return@flow
			}
			if (request.has("remove")) {
				val name = request.get("add") ?: error("Missing key name")
				emitAll(km.remove(name))
				return@flow
			}
			emit(CmdOutput.Data(i18n.get(SecretI18n.InvalidArg()), CmdOutput.Channel.STDERR))
			emit(CmdOutput.Done(1))
			return@flow
		}
	}
	
	private fun handleUnlock(prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> = flow {
		if (core.isPasswordEmpty) {
			logger.debug("Unlock skipped  command=secret  reason=no_password_set")
			emit(CmdOutput.Data(i18n.get(SecretI18n.UnlockNoPassword())))
			emit(CmdOutput.Done())
			return@flow
		}
		
		if (core.isUnlocked) {
			logger.debug("Unlock skipped  command=secret  reason=already_unlocked")
			emit(CmdOutput.Data(i18n.get(SecretI18n.UnlockAlready())))
			emit(CmdOutput.Done())
			return@flow
		}
		
		val password = prompt(i18n.get(SecretI18n.UnlockPrompt()), false).also { emit(CmdOutput.Data("")) }
		
		try {
			core.unlock(password)
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
			if (!core.isUnlocked) {
				core.unlock(password)
			}
			core.changePassword(password, "")
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
		val oldPassword = if (core.isPasswordEmpty) {
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
			if (!core.isUnlocked) {
				core.unlock(oldPassword)
			}
			core.changePassword(oldPassword, newPassword)
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