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

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.api.types.llm.Price
import java.math.BigDecimal
import java.util.*

class ModelAdd(
	private val core: CoreAPI
) : Traceable {
	suspend fun Console.addAll(providerName: String): Nothing {
		val provider = core.config.listProviders().find { it.displayName == providerName }
			?: error(ModelI18n.ProviderNotFound(), providerName)
		val providerMeta = core.config.getProviderMeta(provider.type)
		val modelList =
			core.config.listModels().filter { it.data.providerId == provider.id }.map { it.data.displayName }
		
		providerMeta.models.map { it }.forEach {
			if (it.modelId !in modelList) core.config.setModel(
				CoreConfig.ProviderConfig.Model(
					data = ModelData(
						id = UUID.randomUUID(), displayName = it.modelId, modelInfo = it, providerId = provider.id
					)
				)
			)
		}
		done()
	}
	
	//region 一大坨add的和它的辅助方法
	
	suspend fun Console.add(name: String, provider: String, infoId: String?): Nothing {
		val provider = core.config.listProviders().find { it.displayName == provider }
			?: error(ModelI18n.ProviderNotFound(), provider)
		
		var modelInfo: ModelData.ModelInfo? = null
		core.config.getProviderMeta(provider.type).models.find { it.modelId == infoId }?.let { modelInfo = it }
		
		if (core.config.listModels().any { it.data.displayName == name && it.data.providerId == provider.id })
			error(ModelI18n.ModelDuplicateError(), name)
		
		if (modelInfo == null) {
			val id = promptOrStdin((ModelI18n.PromptId()))
			if (id.isBlank()) invalidValue()
			
			val contextWindow: Int =
				promptOrStdin(ModelI18n.PromptContextWindow()).toIntOrNull()
					?: invalidValue()
			val maxOutputTokens =
				promptOrStdin(ModelI18n.PromptMaxOutputTokens()).toIntOrNull()
					?: invalidValue()
			val price = promptTokenPrice()
			
			suspend fun promptFeature(featureI18n: I18nDef) =
				confirm(ModelI18n.PromptSetFeature(), i18n(featureI18n))
			
			val supportsStreaming = promptFeature(ModelFeature.StreamingFeature())
			val supportsToolCalls = promptFeature(ModelFeature.ToolCallFeature())
			val supportsReasoning = promptFeature(ModelFeature.ReasoningFeature())
			val supportsImage = promptFeature(ModelFeature.ImageFeature())
			val supportsJsonOutput = promptFeature(ModelFeature.JsonOutputFeature())
			
			modelInfo = ModelData.ModelInfo(
				modelId = id,
				contextWindow = contextWindow,
				maxOutputTokens = maxOutputTokens,
				price = price,
				supportsStreaming = supportsStreaming,
				supportsToolCalls = supportsToolCalls,
				supportsReasoning = supportsReasoning,
				supportsImage = supportsImage,
				supportsJsonOutput = supportsJsonOutput
			)
		}
		
		core.config.setModel(
			CoreConfig.ProviderConfig.Model(
				ModelData(
					id = UUID.randomUUID(),
					displayName = name,
					modelInfo = modelInfo,
					providerId = provider.id
				)
			)
		)
		
		done()
	}
	
	private suspend fun Console.promptTokenPrice(): ModelData.TokenPrice {
		val inputPrice = if (confirm(ModelI18n.PromptSetInputPrice()))
			promptPriceTierList()
		else emptyList()
		
		val outputPrice = if (confirm(ModelI18n.PromptSetOutputPrice()))
			promptPriceTierList()
		else emptyList()
		
		return ModelData.TokenPrice(inputPrice, outputPrice)
	}
	
	private suspend fun Console.promptPriceTierList(): List<PriceTier> {
		val priceList = mutableListOf<PriceTier>()
		priceList.add(promptPriceTier())
		while (true) {
			if (!confirm(ModelI18n.PromptSetPrice())) break
			priceList.add(promptPriceTier())
		}
		return priceList.toList()
	}
	
	private suspend fun Console.promptPriceTier(): PriceTier {
		val tieredPrice: Boolean = confirm(ModelI18n.PromptTieredPrice())
		val fromTokens: Int
		val toTokens: Int?
		if (tieredPrice) {
			val result = promptOrStdin(ModelI18n.PromptPriceRange())
				.split("-", limit = 2).map { it.trim() }
			fromTokens = result[0].toIntOrNull() ?: invalidValue()
			val rawTo = result.getOrNull(1)
			toTokens = if (rawTo == null) rawTo else rawTo.toIntOrNull() ?: invalidValue()
		} else {
			fromTokens = 0
			toTokens = null
		}
		val price = promptPrice()
		val cachedPrice: Price? =
			if (confirm(ModelI18n.PromptSetCachedPrice()))
				promptPrice()
			else null
		return PriceTier(fromTokens, toTokens, price, cachedPrice)
	}
	
	private suspend fun Console.promptPrice(): Price {
		val tokenUnit = promptOrStdin(ModelI18n.PromptTokenUnit()).toIntOrNull() ?: invalidValue()
		val currency = trace.catching {
			Currency.getInstance(
				promptOrStdin(ModelI18n.PromptPriceCurrency()).uppercase()
			)
		}.getOrElse { invalidValue() }
		val price = trace.catching {
			BigDecimal(
				promptOrStdin(ModelI18n.PromptPrice(), currency.getDisplayName(i18n.getLanguage()))
			)
		}.getOrElse { invalidValue() }
		return Price(price, currency, tokenUnit)
	}
	
	suspend fun Console.invalidValue(): Nothing = error(ModelI18n.InvalidValue())
	
	//endregion
}
