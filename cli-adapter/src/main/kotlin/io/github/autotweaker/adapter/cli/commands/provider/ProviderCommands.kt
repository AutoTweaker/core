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

package io.github.autotweaker.adapter.cli.commands.provider

import io.github.autotweaker.adapter.cli.commands.CmdOutput
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.Url.Companion.toUrlOrNull
import io.github.autotweaker.api.types.config.CoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.*

class ProviderCommands(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) : Traceable, I18nable {
	fun add(name: String?, type: String?, key: String?, url: String?): Flow<CmdOutput> = flow {
		val name = name ?: promptOrNull(ProvCommandsI18n.PromptName(), ProvCommandsI18n.MissingName()) ?: return@flow
		val type = type ?: promptOrNull(ProvCommandsI18n.PromptType(), ProvCommandsI18n.MissingType()) ?: return@flow
		
		if (core.config.listAvailableProviderTypes().find { it == type } == null) {
			emitI18n(ProvCommandsI18n.InvalidType(), error = true)
			emitDone(1)
			return@flow
		}
		
		val key = key ?: promptOrNull(ProvCommandsI18n.PromptKey(), ProvCommandsI18n.MissingKey()) ?: return@flow
		
		if (core.config.listApiKey().find { it == key } == null) {
			emitI18n(ProvCommandsI18n.InvalidKey(), error = true)
			emitDone(1)
			return@flow
		}
		val baseUrl: Url = if (url != null) {
			url.toUrlOrNull()
		} else {
			promptOrNull(ProvCommandsI18n.PromptUrl())?.toUrlOrNull()
		} ?: core.config.getProviderMeta(type).baseUrl
		
		core.config.setProvider(
			CoreConfig.ProviderConfig.Provider(
				id = UUID.randomUUID(),
				type = type,
				keyId = key,
				baseUrl = baseUrl,
				displayName = name,
				errorHandlingRules = core.config.getProviderMeta(type).errorHandlingRules,
			)
		)
		emitDone()
	}
	
	fun remove(name: String, yes: Boolean): Flow<CmdOutput> = flow {
		val ids = core.config.listProviders().filter { it.displayName == name }.map { it.id }
		if (ids.isEmpty()) {
			emitI18n(ProvI18n.ProviderNotFound(), name, error = true)
			emitDone(1)
			return@flow
		}
		if (!yes) {
			emitI18n(
				ProvCommandsI18n.RemoveListCount(), core.config.listProviders().count { it.displayName == name })
			ids.forEach { emit(CmdOutput.Data(it.toString())) }
			val sure = promptOrNull(ProvCommandsI18n.RemoveConfirm())?.trim()
			if (sure != "yes" && sure != "y") {
				emitDone(1)
				return@flow
			}
		}
		
		ids.forEach { core.config.removeProvider(it) }
		emitDone()
	}
	
	fun rename(name: String, new: String): Flow<CmdOutput> = flow {
		val provider = core.config.listProviders().find { it.displayName == name } ?: run {
			emitI18n(ProvI18n.ProviderNotFound(), name, error = true)
			emitDone(1)
			return@flow
		}
		
		if (core.config.listProviders().any { it.displayName == new }) {
			emitI18n(ProvCommandsI18n.ProviderExistsError(), new, error = true)
			emitDone(1)
			return@flow
		}
		
		core.config.setProvider(
			provider.copy(displayName = new)
		)
		emitDone()
	}
	
	
	private suspend fun FlowCollector<CmdOutput>.promptOrNull(
		def: I18nDef, defOnEmpty: I18nDef? = null
	): String? {
		val result = prompt(i18n(def), true)
		if (result.isBlank()) {
			defOnEmpty?.let {
				emitI18n(it, error = true)
				emitDone(1)
			}
			return null
		}
		return result
	}
}
