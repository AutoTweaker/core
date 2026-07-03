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

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.types.config.CoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KeyManager(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) : I18nable {
	fun list(): Flow<CmdOutput> = flow {
		core.config.listApiKey().forEach { emit(CmdOutput.Data(it)) }
		emitDone()
	}
	
	fun add(name: String): Flow<CmdOutput> = flow {
		if (name.isBlank()) {
			emitI18n(SecretI18n.EmptyNameError(), error = true)
			emitDone(1)
			return@flow
		}
		val key = prompt(i18n(SecretI18n.PromptInputApiKey()), false)
		
		if (key.isBlank()) {
			emitI18n(SecretI18n.EmptyKeyError(), error = true)
			emitDone(1)
			return@flow
		}
		if (core.config.listApiKey().any { it == name }) {
			emitI18n(SecretI18n.KeyExistsError(), name, error = true)
			emitDone(1)
			return@flow
		}
		
		core.config.addApiKey(
			CoreConfig.ProviderConfig.ApiKey(
				name, key
			)
		)
		emitDone()
	}
	
	fun remove(name: String): Flow<CmdOutput> = flow {
		if (core.config.listApiKey().any { it == name }) {
			core.config.removeApiKey(name)
			emitDone()
		} else {
			emitI18n(SecretI18n.KeyNotFoundError(), name, error = true)
			emitDone(1)
		}
	}
}
