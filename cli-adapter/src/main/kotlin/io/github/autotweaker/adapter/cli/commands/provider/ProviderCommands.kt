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

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.*

internal class ProviderCommands(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) {
	private val i18n: I18nService get() = core.i18n.i18nService
	
	fun add(name: String?, type: String?, key: String?, url: String?): Flow<CmdOutput> = flow {
		val name =
			name ?: promptOrNull(ProvCommandsI18n.PromptAddName(), ProvCommandsI18n.OutAddMissingName()) ?: return@flow
		val type =
			type ?: promptOrNull(ProvCommandsI18n.PromptAddType(), ProvCommandsI18n.OutAddMissingType()) ?: return@flow
		
		if (core.config.listAvailableProviderTypes().find { it == type } == null) {
			emitI18n(ProvCommandsI18n.OutAddInvalidType(), error = true)
			emit(CmdOutput.Done(1))
			return@flow
		}
		
		val key =
			key ?: promptOrNull(ProvCommandsI18n.PromptAddKey(), ProvCommandsI18n.OutAddMissingKey()) ?: return@flow
		
		if (core.config.listApiKeyNames().find { it == key } == null) {
			emitI18n(ProvCommandsI18n.OutAddInvalidKey(), error = true)
			emitDone()
			return@flow
		}
		
		val urlString = url ?: promptOrNull(ProvCommandsI18n.PromptAddUrl())
		
		val url = urlString?.let {
			try {
				Url(it)
			} catch (e: IllegalArgumentException) {
				emitI18n(ProvCommandsI18n.OutAddInvalidUrl(), e.message ?: "Unknown Error", error = true)
				emitDone()
				return@flow
			}
		} ?: core.config.getProviderMeta(type).baseUrl
		
		core.config.addProvider(
			CoreConfig.ProviderConfig.Provider(
				id = UUID.randomUUID(),
				type = type,
				keyId = key,
				baseUrl = url,
				displayName = name,
				errorHandlingRules = core.config.getProviderMeta(type).errorHandlingRules,
			)
		)
		emitDone(0)
	}
	
	fun remove(name: String, yes: Boolean): Flow<CmdOutput> = flow {
		val ids = core.config.listProviders().filter { it.displayName == name }.map { it.id }
		if (ids.isEmpty()) {
			emitI18n(ProvCommandsI18n.OutRemoveNotFound(), name, error = true)
			emitDone()
			return@flow
		}
		if (!yes) {
			emitI18n(ProvCommandsI18n.PromptRemoveList(), core.config.listProviders().count { it.displayName == name })
			ids.forEach { emit(CmdOutput.Data(it.toString())) }
			val sure = promptOrNull(ProvCommandsI18n.PromptRemoveSure())
			if (sure != "yes" && sure != "y") {
				emitDone(1)
				return@flow
			}
		}
		ids.forEach { core.config.removeProvider(it) }
	}
	
	private suspend fun FlowCollector<CmdOutput>.promptOrNull(
		def: I18nDef, defOnEmpty: I18nDef? = null
	): String? {
		val result = prompt(i18n.get(def) + " ", true)
		if (result.isBlank()) {
			defOnEmpty?.let {
				emit(CmdOutput.Data(i18n.get(it), CmdOutput.Channel.STDERR))
				emitDone()
			}
			return null
		}
		return result
	}
	
	private suspend fun FlowCollector<CmdOutput>.emitI18n(def: I18nDef, vararg args: Any, error: Boolean = false) =
		emit(
			CmdOutput.Data(
				i18n.get(def).format(*args),
				if (error) CmdOutput.Channel.STDERR else CmdOutput.Channel.STDOUT
			)
		)
	
	private suspend fun FlowCollector<CmdOutput>.emitDone(exitCode: Int = 1) = emit(CmdOutput.Done(exitCode))
}