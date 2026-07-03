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
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.types.config.CoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class EnvManager(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) : I18nable {
	
	fun list(type: EnvType): Flow<CmdOutput> = flow {
		core.config.listEnv(
			type.toCoreConfig()
		).forEach {
			emit(CmdOutput.Data(it))
		}.also {
			emitDone()
		}
	}
	
	fun add(type: EnvType, name: String): Flow<CmdOutput> = flow {
		val value = prompt(i18n(SecretI18n.PromptInputApiKey()), false)
		core.config.setEnv(CoreConfig.JsonConfig.Env(name, value, type.toCoreConfig()))
		emitDone()
	}
	
	fun get(type: EnvType, name: String): Flow<CmdOutput> = flow {
		core.config.getEnv(type.toCoreConfig(), name)?.let { emit(CmdOutput.Data(it)) }
		emitDone()
	}
	
	fun remove(type: EnvType, name: String): Flow<CmdOutput> = flow {
		core.config.removeEnv(type.toCoreConfig(), name)
		emitDone()
	}
	
	private fun EnvType.toCoreConfig(): CoreConfig.JsonConfig.Env.Type = when (this) {
		EnvType.BASH -> CoreConfig.JsonConfig.Env.Type.BASH_ENV
		EnvType.CONTAINER -> CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV
	}
	
	enum class EnvType { BASH, CONTAINER }
}
