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

package io.github.autotweaker.adapter.cli.commands.model

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.exception.notfound.ModelNotFoundException
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.Price
import java.util.*

@AutoService(Command::class)
class Model : Command, Traceable {
	override val name = "model"
	override val description = i18n(ModelI18n.Description())
	override val syntax = buildSyntax(XOR) {
		flag("list", ModelI18n.ParamList())
		all {
			flag("add", ModelI18n.ParamAdd())
			value("name", ModelI18n.ParamName())
			value("provider", ModelI18n.ParamProvider())
			value("info", ModelI18n.ParamAddInfo()) { required = false }
		}
		value("add-all", ModelI18n.ParamAddAll()) { aliases() }
		
		all {
			xor {
				flag("remove", ModelI18n.ParamRemove()) { aliases("rm") }
				flag("show", ModelI18n.ParamShow())
				flag("set-default", ModelI18n.ParamDefault()) { aliases() }
			}
			positional("provider", ModelI18n.ParamProvider())
			positional("model", ModelI18n.ParamName())
		}
		
		xor {
			flag("get-default", ModelI18n.ParamGetDefault()) { aliases() }
			flag("reset-default", ModelI18n.ParamResetDefault()) { aliases() }
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("list") {
			list(core)
		}
		
		val add = ModelAdd(core)
		
		handleValue("add-all") {
			with(add) {
				addAll(it)
			}
		}
		
		handleFlag("add") {
			val name: String = getValue("name")
			val provider: String = getValue("provider")
			val info: String? = getValueOrNull("info")
			with(add) {
				add(name, provider, info)
			}
		}
		
		handleFlag("remove") {
			val id = findModel(core)
			core.config.removeModel(id)
		}
		
		handleFlag("show") {
			val model = core.config.getModel(findModel(core))?.data ?: done(1)
			out(ModelI18n.ModelName(), model.displayName)
			out(ModelI18n.ProviderName(), getPositional(0))
			printModelInfo(model.modelInfo)
			
		}
		
		handleFlag("set-default") {
			core.config.setDefaultModel(findModel(core))
		}
		
		handleFlag("get-default") {
			core.config.getDefaultModel()?.let { printModel(it, core) } ?: out("null")
		}
		
		handleFlag("reset-default") {
			core.config.setDefaultModel(null)
		}
		
		done(1)
	}
	
	private suspend fun Console.list(core: CoreAPI) {
		core.config.listModels().forEach { model ->
			printModel(model.data.id, core)
		}
	}
	
	private suspend fun Console.printModel(id: UUID, core: CoreAPI) {
		val model = core.config.getModel(id) ?: throw ModelNotFoundException(id)
		val providerName = core.config.getProvider(model.data.providerId)?.displayName ?: i18n(ModelI18n.Unknown())
		out("[$providerName] ${model.data.displayName}")
	}
	
	companion object : I18nable {
		suspend fun Console.findModel(core: CoreAPI): UUID {
			val provider = getPositional(0)
			val model = getPositional(1)
			val providerId = core.config.listProviders().find { it.displayName == provider }?.id
				?: error(ModelI18n.ProviderNotFound(), provider)
			val modelId = core.config.listModels()
				.find { it.data.displayName == model && it.data.providerId == providerId }?.data?.id
				?: error(ModelI18n.ModelNotFound(), model)
			return modelId
		}
		
		suspend fun Console.printModelInfo(info: ModelData.ModelInfo) {
			val feature = buildList {
				if (info.supportsStreaming) add(i18n(ModelFeature.StreamingFeature()))
				if (info.supportsToolCalls) add(i18n(ModelFeature.ToolCallFeature()))
				if (info.supportsReasoning) add(i18n(ModelFeature.ReasoningFeature()))
				if (info.supportsImage) add(i18n(ModelFeature.ImageFeature()))
				if (info.supportsJsonOutput) add(i18n(ModelFeature.JsonOutputFeature()))
			}.joinToString(separator = SPACE.toString()) { "[${it}]" }
			
			out(ModelI18n.ModelId(), info.modelId)
			out(ModelI18n.ContextWindow(), formatUnit(info.contextWindow))
			out(ModelI18n.MaxOutput(), formatUnit(info.maxOutputTokens))
			out(ModelI18n.ModelFeature(), feature)
			printTokenPrice(info.price)
		}
		
		private suspend fun Console.printTokenPrice(price: ModelData.TokenPrice) {
			suspend fun formatPrice(price: List<ModelData.TokenPrice.PriceTier>) {
				price.forEach {
					val from = it.fromTokens
					val to = it.toTokens
					out(
						INDENT + when {
							from == 0 && to == null -> buildPrice(it.price, it.cachedPrice)
							to == null -> "[${formatUnit(from)}+] ${
								buildPrice(
									it.price, it.cachedPrice
								)
							}"
							
							else -> "[${formatUnit(from)} - ${formatUnit(to)}] ${
								buildPrice(
									it.price, it.cachedPrice
								)
							}"
						}
					)
				}
			}
			
			out(ModelI18n.InputPrice())
			formatPrice(price.inputPrice)
			out(ModelI18n.OutputPrice())
			formatPrice(price.outputPrice)
		}
		
		
		private fun buildPrice(price: Price, cached: Price?): String {
			fun formatPrice(price: Price) =
				"${price.amount.toPlainString()} ${price.currency} / ${formatUnit(price.tokenUnit)} tokens"
			
			if (cached == null) return formatPrice(price)
			return "${formatPrice(price)} ${i18n(ModelI18n.Or())} ${formatPrice(cached)} ${
				i18n(ModelI18n.CachedPrice())
			}"
		}
		
		private fun formatUnit(number: Int): String = when {
			number == 0 -> 0
			number % 1_000_000 == 0 -> "${number / 1_000_000}m"
			number % 1_000 == 0 -> "${number / 1_000}k"
			else -> number
		}.toString()
	}
}
