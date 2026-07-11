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
import io.github.autotweaker.adapter.cli.commands.ModelFeature
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.Price
import io.github.autotweaker.api.types.llm.ProviderData
import kotlinx.coroutines.flow.*

class ProviderQueries(private val core: CoreAPI) : I18nable {
	fun list(): Flow<String> = flow {
		core.config.listProviders().forEachBetween({ provider ->
			val modelCount = core.config.listModels().count { it.data.providerId == provider.id }
			emit(i18n(ProvQueriesI18n.Name()).format(provider.displayName))
			emit(i18n(ProvQueriesI18n.Type()).format(provider.type))
			emit(i18n(ProvQueriesI18n.Model()).format(modelCount))
		}, between = { emit(LINE) })
	}
	
	fun show(name: String): Flow<String> = flow {
		val provider = core.config.listProviders().firstOrNull { it.displayName == name } ?: return@flow
		emit(i18n(ProvQueriesI18n.Name()).format(provider.displayName))
		emit(i18n(ProvQueriesI18n.Type()).format(provider.type))
		emit(i18n(ProvQueriesI18n.Key()).format(provider.keyId))
		emit(
			i18n(ProvQueriesI18n.Url()).format(provider.baseUrl?.value ?: i18n(ProvQueriesI18n.Default()))
		)
		provider.errorHandlingRules?.let {
			emit(i18n(ProvQueriesI18n.Rule()))
			emitAll(printRules(it))
		} ?: emit(i18n(ProvQueriesI18n.Rule()) + SPACE + i18n(ProvQueriesI18n.Default()))
	}
	
	fun types(): Flow<String> = core.config.listAvailableProviderTypes().asFlow()
	
	fun info(name: String): Flow<CmdOutput> = flow {
		if (!core.config.listAvailableProviderTypes().any { it == name }) {
			emitI18n(ProvI18n.ProviderNotFound(), name, error = true)
			emitDone(1)
			return@flow
		}
		
		val meta = core.config.getProviderMeta(name)
		emitI18n(ProvQueriesI18n.Name(), meta.name)
		emitI18n(ProvQueriesI18n.Url(), meta.baseUrl.value)
		emitI18n(ProvQueriesI18n.Rule())
		emitAll(printRules(meta.errorHandlingRules).map { CmdOutput.Data(it) })
		meta.models.forEach {
			emit(CmdOutput.Data(LINE))
			emitAll(printModelInfo(it).map { info -> CmdOutput.Data(info) })
		}
		emitDone()
	}
	
	private fun printRules(rules: List<ProviderData.ErrorHandlingRule>): Flow<String> = flow {
		rules.forEach {
			emit(
				INDENT + i18n(ProvQueriesI18n.StatusCode()).format(it.statusCode) + " | " + i18n(
					ProvQueriesI18n.Strategy()
				).format(it.strategy)
			)
		}
	}
	
	private fun printModelInfo(info: ModelData.ModelInfo): Flow<String> = flow {
		val feature = buildList {
			if (info.supportsStreaming) add(i18n(ModelFeature.StreamingFeature()))
			if (info.supportsToolCalls) add(i18n(ModelFeature.ToolCallFeature()))
			if (info.supportsReasoning) add(i18n(ModelFeature.ReasoningFeature()))
			if (info.supportsImage) add(i18n(ModelFeature.ImageFeature()))
			if (info.supportsJsonOutput) add(i18n(ModelFeature.JsonOutputFeature()))
		}.joinToString(separator = SPACE.toString()) { "[${it}]" }
		
		emit(i18n(ProvQueriesI18n.ModelId()).format(info.modelId))
		emit(i18n(ProvQueriesI18n.ContextWindow()).format(processUnit(info.contextWindow)))
		emit(i18n(ProvQueriesI18n.MaxOutput()).format(processUnit(info.maxOutputTokens)))
		emit(i18n(ProvQueriesI18n.ModelFeature()).format(feature))
		emitAll(printTokenPrice(info.price))
	}
	
	private fun printTokenPrice(price: ModelData.TokenPrice): Flow<String> = flow {
		fun processPrice(price: List<ModelData.TokenPrice.PriceTier>): Flow<String> = flow {
			price.forEach {
				val from = it.fromTokens
				val to = it.toTokens
				emit(
					when {
						from == 0 && to == null -> INDENT + buildPrice(it.price, it.cachedPrice)
						to == null -> INDENT + "[${processUnit(from)}+] ${
							buildPrice(
								it.price, it.cachedPrice
							)
						}"
						
						else -> INDENT + "[${processUnit(from)} - ${processUnit(to)}] ${
							buildPrice(
								it.price, it.cachedPrice
							)
						}"
					}
				)
			}
		}
		emit(i18n(ProvQueriesI18n.InputPrice()))
		emitAll(processPrice(price.inputPrice))
		emit(i18n(ProvQueriesI18n.OutputPrice()))
		emitAll(processPrice(price.outputPrice))
	}
	
	
	private fun buildPrice(price: Price, cached: Price?): String {
		fun processPrice(price: Price) =
			"${price.amount.toPlainString()} ${price.currency} / ${processUnit(price.tokenUnit)} tokens"
		
		if (cached == null) return processPrice(price)
		return "${processPrice(price)} ${i18n(ProvQueriesI18n.Or())} ${processPrice(cached)} ${
			i18n(
				ProvQueriesI18n.CachedPrice()
			)
		}"
	}
	
	private fun processUnit(number: Int): String = when {
		number == 0 -> 0.toString()
		number % 1_000_000 == 0 -> "${number / 1_000_000}m"
		number % 1_000 == 0 -> "${number / 1_000}k"
		else -> number.toString()
	}
}
