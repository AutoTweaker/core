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

package io.github.autotweaker.core.adapter.impl.cli.commands.provider

import io.github.autotweaker.api.CoreAPI
import io.github.autotweaker.api.types.Price
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class Read(private val core: CoreAPI) {
	fun list(): Flow<String> = flow {
		val providers = core.config.listProviders()
		providers.forEachIndexed { index, provider ->
			val modelCount = core.config.listModelIds().count { provider.name == it.provider }
			emit(I18n.get("prov.out.name", provider.name))
			emit(I18n.get("prov.out.type", provider.type))
			emit(I18n.get("prov.out.model", modelCount))
			if (index != providers.lastIndex) emit(LINE)
		}
	}
	
	fun show(name: String): Flow<String> = flow {
		val provider = core.config.listProviders().firstOrNull { it.name == name } ?: return@flow
		emit(I18n.get("prov.out.name", provider.name))
		emit(I18n.get("prov.out.type", provider.type))
		emit(I18n.get("prov.out.key", provider.keyId))
		emit(I18n.get("prov.out.url", provider.baseUrl?.value ?: I18n.get("prov.out.default")))
		provider.errorHandlingRules?.let {
			emit(I18n.get("prov.out.rule"))
			emitAll(printRules(it))
		} ?: emit(I18n.get("prov.out.rule") + " " + I18n.get("prov.out.default"))
	}
	
	fun types(): Flow<String> = core.config.listAvailableProviderTypes().asFlow()
	
	fun info(name: String): Flow<String> = flow {
		val meta = core.config.getProviderMeta(name)
		emit(I18n.get("prov.out.name", meta.name))
		emit(I18n.get("prov.out.url", meta.baseUrl.value))
		emit(I18n.get("prov.out.rule"))
		emitAll(printRules(meta.errorHandlingRules))
		meta.models.forEach {
			emit(LINE)
			emitAll(printModelInfo(it))
		}
	}
	
	private fun printRules(rules: List<ProviderData.ErrorHandlingRule>): Flow<String> = flow {
		rules.forEach {
			emit(
				SPACE + I18n.get("prov.out.rule.status", it.statusCode) + " | " + I18n.get(
					"prov.out.rule.strategy", it.strategy
				)
			)
		}
	}
	
	private fun printModelInfo(info: ProviderData.ModelData.ModelInfo): Flow<String> = flow {
		val feature = buildList {
			if (info.supportsStreaming) add(I18n.get("prov.out.model.feature.streaming"))
			if (info.supportsToolCalls) add(I18n.get("prov.out.model.feature.tool_call"))
			if (info.supportsReasoning) add(I18n.get("prov.out.model.feature.reasoning"))
			if (info.supportsImage) add(I18n.get("prov.out.model.feature.image"))
			if (info.supportsJsonOutput) add(I18n.get("prov.out.model.feature.json_output"))
		}.joinToString(separator = " ") { "[${it}]" }
		emit(I18n.get("prov.out.model.id", info.id))
		emit(I18n.get("prov.out.model.context_window", processUnit(info.contextWindow)))
		emit(I18n.get("prov.out.model.max_output", processUnit(info.maxOutputTokens)))
		emit(I18n.get("prov.out.model.feature", feature))
		emitAll(printTokenPrice(info.price))
	}
	
	private fun printTokenPrice(price: ProviderData.ModelData.TokenPrice): Flow<String> = flow {
		fun processPrice(price: List<ProviderData.ModelData.TokenPrice.PriceTier>): Flow<String> = flow {
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
		emit(I18n.get("prov.out.model.price.input"))
		emitAll(processPrice(price.inputPrice))
		emit(I18n.get("prov.out.model.price.output"))
		emitAll(processPrice(price.outputPrice))
	}
	
	
	private fun buildPrice(price: Price, cached: Price?): String {
		fun processPrice(price: Price) =
			"${price.amount.toPlainString()} ${price.currency} / ${processUnit(price.unit)} tokens"
		
		if (cached == null) return processPrice(price)
		return "${processPrice(price)} ${I18n.get("prov.out.or")} ${processPrice(cached)} ${I18n.get("prov.out.model.price.cached")}"
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