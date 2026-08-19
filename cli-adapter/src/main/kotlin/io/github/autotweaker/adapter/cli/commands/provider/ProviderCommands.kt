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

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.guava.inverse
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.Url.Companion.toUrlOrNull
import io.github.autotweaker.api.types.llm.ProviderData

class ProviderCommands(
	private val core: CoreAPI
) : Traceable {
	suspend fun Console.add(name: String?, type: String?, key: String?, url: String?) {
		val name = name ?: promptOrError(
			ProvCommandsI18n.PromptName(), ProvCommandsI18n.MissingName()
		)
		if (core.config.listProviders().any { it.displayName == name })
			error(ProvCommandsI18n.ProviderExistsError(), name)
		
		val type = type ?: promptOrError(
			ProvCommandsI18n.PromptType(), ProvCommandsI18n.MissingType()
		)
		
		val meta = core.config.getProviderMeta(type)
		
		val key = key ?: promptOrError(
			ProvCommandsI18n.PromptKey(), ProvCommandsI18n.MissingKey()
		)
		
		val keyUUID = core.config.listApiKey().inverse[key]
			?: error(ProvCommandsI18n.InvalidKey(), key)
		
		
		val baseUrl = if (url != null) {
			url.toUrlOrNull()
		} else {
			promptOrNull(ProvCommandsI18n.PromptUrl())?.toUrlOrNull()
		}
		
		core.config.setProvider(
			ProviderData(
				id = UUID(),
				providerType = type,
				apiKey = keyUUID,
				baseUrl = baseUrl ?: meta.baseUrl,
				displayName = name,
				errorHandlingRules = meta.errorHandlingRules,
			)
		)
	}
	
	suspend fun Console.remove(name: String, yes: Boolean) {
		val id = core.config.listProviders().find { it.displayName == name }?.id
			?: error(ProvI18n.ProviderNotFound(), name)
		val models = core.config.listModels().filter { it.providerId == id }
		
		if (!yes && !confirm(ProvCommandsI18n.RemoveConfirm(), name, models.count()))
			done(1)
		
		core.config.removeProvider(id)
	}
	
	suspend fun Console.rename(name: String, new: String) {
		val provider = core.config.listProviders().find { it.displayName == name }
			?: error(ProvI18n.ProviderNotFound(), name)
		
		core.config.setProvider(
			provider.copy(displayName = new)
		)
	}
	
	private suspend fun Console.promptOrError(
		def: I18nDef, defOnEmpty: I18nDef
	): String = prompt(def).orNull() ?: error(defOnEmpty)
	
	private fun String.orNull() = ifBlank { null }
}
