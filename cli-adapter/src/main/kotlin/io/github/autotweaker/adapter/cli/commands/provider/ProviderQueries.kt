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
import io.github.autotweaker.adapter.cli.commands.model.Model.Companion.printModelInfo
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.llm.ProviderData

class ProviderQueries(private val core: CoreAPI) : I18nable {
	suspend fun Console.list() {
		core.config.listProviders().forEachBetween({ provider ->
			val modelCount = core.config.listModels().count { it.data.providerId == provider.id }
			out(ProvQueriesI18n.Name(), provider.displayName)
			out(ProvQueriesI18n.Type(), provider.type)
			out(ProvQueriesI18n.Model(), modelCount)
		}, between = { out(LINE) })
	}
	
	suspend fun Console.show(name: String) {
		val provider = core.config.listProviders().firstOrNull { it.displayName == name }
			?: error(ProvI18n.ProviderNotFound(), name)
		out(ProvQueriesI18n.Name(), provider.displayName)
		out(ProvQueriesI18n.Type(), provider.type)
		out(ProvQueriesI18n.Key(), provider.keyId)
		out(ProvQueriesI18n.Url(), provider.baseUrl?.value ?: i18n(ProvQueriesI18n.Default()))
		
		provider.errorHandlingRules?.let {
			out(ProvQueriesI18n.Rule())
			printRules(it)
		} ?: out(i18n(ProvQueriesI18n.Rule()) + SPACE + i18n(ProvQueriesI18n.Default()))
	}
	
	suspend fun Console.types() {
		core.config.listAvailableProviderTypes().forEach {
			out(it)
		}
	}
	
	suspend fun Console.info(name: String) {
		val meta = core.config.getProviderMeta(name)
		out(ProvQueriesI18n.Name(), meta.name)
		out(ProvQueriesI18n.Url(), meta.baseUrl.value)
		out(ProvQueriesI18n.Rule())
		printRules(meta.errorHandlingRules)
		meta.models.forEach {
			out(LINE)
			printModelInfo(it)
		}
	}
	
	private suspend fun Console.printRules(rules: List<ProviderData.ErrorHandlingRule>) {
		rules.forEach {
			out(
				INDENT + i18n(ProvQueriesI18n.StatusCode(), it.statusCode) + " | " + i18n(
					ProvQueriesI18n.Strategy(), it.strategy
				)
			)
		}
	}
}
