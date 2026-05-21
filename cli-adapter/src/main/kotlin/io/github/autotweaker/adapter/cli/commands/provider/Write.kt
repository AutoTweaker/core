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

import io.github.autotweaker.adapter.cli.Command
import io.github.autotweaker.adapter.cli.i18n.I18n
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.*

class Write(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) {
	fun add(name: String?, type: String?, key: String?, url: String?): Flow<Command.Chunk> = flow {
		val name = name ?: promptOrNull("prov.prompt.add.name", "prov.out.add.missing.name") ?: return@flow
		val type = type ?: promptOrNull("prov.prompt.add.type", "prov.out.add.missing.type") ?: return@flow
		
		if (core.config.listAvailableProviderTypes().find { it == type } == null) {
			emitI18n("prov.out.add.invalid.type")
			emit(Command.Chunk.Done(1))
			return@flow
		}
		
		val key = key ?: promptOrNull("prov.prompt.add.key", "prov.out.add.missing.key") ?: return@flow
		
		if (core.config.listApiKeyNames().find { it == key } == null) {
			emitI18n("prov.out.add.invalid.key")
			emitDone()
			return@flow
		}
		
		val urlString = url ?: promptOrNull("prov.prompt.add.url")
		
		val url = urlString?.let {
			try {
				Url(it)
			} catch (e: IllegalArgumentException) {
				emitI18n("prov.out.add.invalid.url", e.message ?: "Unknown Error")
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
	
	fun remove(name: String, yes: Boolean): Flow<Command.Chunk> = flow {
		val ids = core.config.listProviders().filter { it.displayName == name }.map { it.id }
		if (ids.isEmpty()) {
			emitI18n("prov.out.remove.not.found", name)
			emitDone()
			return@flow
		}
		if (!yes) {
			emitI18n("prov.prompt.remove.list", core.config.listProviders().count { it.displayName == name })
			ids.forEach { emit(Command.Chunk.Data(it.toString())) }
			val sure = promptOrNull("prov.prompt.remove.sure")
			if (sure != "yes" && sure != "y") {
				emitDone(1)
				return@flow
			}
		}
		ids.forEach { core.config.removeProvider(it) }
	}
	
	private suspend fun FlowCollector<Command.Chunk>.promptOrNull(
		i18nKey: String, i18nKeyOnEmpty: String? = null
	): String? {
		val result = prompt(I18n.get(i18nKey) + " ", true)
		if (result.isBlank()) {
			i18nKeyOnEmpty?.let {
				emit(Command.Chunk.Data(I18n.get(it)))
				emitDone()
			}
			return null
		}
		return result
	}
	
	private suspend fun FlowCollector<Command.Chunk>.emitI18n(key: String, vararg args: Any) =
		emit(Command.Chunk.Data(I18n.get(key, args)))
	
	private suspend fun FlowCollector<Command.Chunk>.emitDone(exitCode: Int = 1) = emit(Command.Chunk.Done(exitCode))
}