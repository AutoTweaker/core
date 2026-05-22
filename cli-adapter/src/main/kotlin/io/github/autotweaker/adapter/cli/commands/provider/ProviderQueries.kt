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

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.Price
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ProviderData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class ProviderQueries(private val core: CoreAPI) {
	private val i18n: I18nService get() = core.i18nService()
	fun list(): Flow<String> = flow {
		val providers = core.config.listProviders()
		providers.forEachIndexed { index, provider ->
			val modelCount = core.config.listModels().count { it.data.providerId == provider.id }
			emit(i18n.get(ProvQueriesI18n.OutName()).format(provider.displayName))
			emit(i18n.get(ProvQueriesI18n.OutType()).format(provider.type))
			emit(i18n.get(ProvQueriesI18n.OutModel()).format(modelCount))
			if (index != providers.lastIndex) emit(LINE)
		}
	}
	
	fun show(name: String): Flow<String> = flow {
		val provider = core.config.listProviders().firstOrNull { it.displayName == name } ?: return@flow
		emit(i18n.get(ProvQueriesI18n.OutName()).format(provider.displayName))
		emit(i18n.get(ProvQueriesI18n.OutType()).format(provider.type))
		emit(i18n.get(ProvQueriesI18n.OutKey()).format(provider.keyId))
		emit(
			i18n.get(ProvQueriesI18n.OutUrl()).format(provider.baseUrl?.value ?: i18n.get(ProvQueriesI18n.OutDefault()))
		)
		provider.errorHandlingRules?.let {
			emit(i18n.get(ProvQueriesI18n.OutRule()))
			emitAll(printRules(it))
		} ?: emit(i18n.get(ProvQueriesI18n.OutRule()) + " " + i18n.get(ProvQueriesI18n.OutDefault()))
	}
	
	fun types(): Flow<String> = core.config.listAvailableProviderTypes().asFlow()
	
	fun info(name: String): Flow<String> = flow {
		val meta = core.config.getProviderMeta(name)
		emit(i18n.get(ProvQueriesI18n.OutName()).format(meta.name))
		emit(i18n.get(ProvQueriesI18n.OutUrl()).format(meta.baseUrl.value))
		emit(i18n.get(ProvQueriesI18n.OutRule()))
		emitAll(printRules(meta.errorHandlingRules))
		meta.models.forEach {
			emit(LINE)
			emitAll(printModelInfo(it))
		}
	}
	
	private fun printRules(rules: List<ProviderData.ErrorHandlingRule>): Flow<String> = flow {
		rules.forEach {
			emit(
				SPACE + i18n.get(ProvQueriesI18n.OutRuleStatus()).format(it.statusCode) + " | " + i18n.get(
					ProvQueriesI18n.OutRuleStrategy()
				).format(it.strategy)
			)
		}
	}
	
	private fun printModelInfo(info: ModelData.ModelInfo): Flow<String> = flow {
		val feature = buildList {
			if (info.supportsStreaming) add(i18n.get(ProvQueriesI18n.OutModelFeatureStreaming()))
			if (info.supportsToolCalls) add(i18n.get(ProvQueriesI18n.OutModelFeatureToolCall()))
			if (info.supportsReasoning) add(i18n.get(ProvQueriesI18n.OutModelFeatureReasoning()))
			if (info.supportsImage) add(i18n.get(ProvQueriesI18n.OutModelFeatureImage()))
			if (info.supportsJsonOutput) add(i18n.get(ProvQueriesI18n.OutModelFeatureJsonOutput()))
		}.joinToString(separator = " ") { "[${it}]" }
		emit(i18n.get(ProvQueriesI18n.OutModelId()).format(info.modelId))
		emit(i18n.get(ProvQueriesI18n.OutModelContextWindow()).format(processUnit(info.contextWindow)))
		emit(i18n.get(ProvQueriesI18n.OutModelMaxOutput()).format(processUnit(info.maxOutputTokens)))
		emit(i18n.get(ProvQueriesI18n.OutModelFeature()).format(feature))
		emitAll(printTokenPrice(info.price))
	}
	
	private fun printTokenPrice(price: ModelData.TokenPrice): Flow<String> = flow {
		fun processPrice(price: List<ModelData.TokenPrice.PriceTier>): Flow<String> = flow {
			price.forEach {
				val from = it.fromTokens
				val to = it.toTokens
				emit(
					when {
						from == 0 && to == null -> SPACE + buildPrice(it.price, it.cachedPrice)
						to == null -> SPACE + "[${processUnit(from)}+] ${
							buildPrice(
								it.price, it.cachedPrice
							)
						}"
						
						else -> SPACE + "[${processUnit(from)} - ${processUnit(to)}] ${
							buildPrice(
								it.price, it.cachedPrice
							)
						}"
						
					}
				)
			}
		}
		emit(i18n.get(ProvQueriesI18n.OutModelPriceInput()))
		emitAll(processPrice(price.inputPrice))
		emit(i18n.get(ProvQueriesI18n.OutModelPriceOutput()))
		emitAll(processPrice(price.outputPrice))
	}
	
	
	private fun buildPrice(price: Price, cached: Price?): String {
		fun processPrice(price: Price) =
			"${price.amount.toPlainString()} ${price.currency} / ${processUnit(price.unit)} tokens"
		
		if (cached == null) return processPrice(price)
		return "${processPrice(price)} ${i18n.get(ProvQueriesI18n.OutOr())} ${processPrice(cached)} ${
			i18n.get(
				ProvQueriesI18n.OutModelPriceCached()
			)
		}"
	}
	
	private fun processUnit(number: Int): String = when {
		number == 0 -> 0.toString()
		number % 1_000_000 == 0 -> "${number / 1_000_000}m"
		number % 1_000 == 0 -> "${number / 1_000}k"
		else -> number.toString()
	}
	
	companion object {
		const val SPACE = "    "
		val LINE = "-".repeat(10)
	}
}