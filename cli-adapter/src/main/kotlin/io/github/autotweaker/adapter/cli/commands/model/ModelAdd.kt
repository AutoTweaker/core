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

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.adapter.cli.commands.ModelFeature
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.api.types.llm.Price
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.util.*

class ModelAdd(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) : I18nable, Traceable {
	fun addAll(providerName: String): Flow<CmdOutput> = flow {
		val provider = core.config.listProviders().find { it.displayName == providerName } ?: run {
			emitI18n(ModelI18n.ProviderNotFound(), error = true)
			emitDone(1)
			return@flow
		}
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
		emitDone()
	}
	
	//region 一大坨add的和它的辅助方法
	
	fun add(name: String, provider: String, infoId: String?): Flow<CmdOutput> = flow {
		val provider = core.config.listProviders().find { it.displayName == provider } ?: run {
			emitI18n(ModelI18n.ProviderNotFound(), provider, error = true)
			emitDone(1)
			return@flow
		}
		
		var modelInfo: ModelData.ModelInfo? = null
		core.config.getProviderMeta(provider.type).models.find { it.modelId == infoId }?.let { modelInfo = it }
		
		if (core.config.listModels().any { it.data.displayName == name && it.data.providerId == provider.id }) {
			emitI18n(ModelI18n.ModelDuplicateError(), name, error = true)
			emitDone(1)
			return@flow
		}
		
		suspend fun invalidValue() {
			emitI18n(
				ModelI18n.InvalidValue(), error = true
			)
			emitDone(1)
		}
		
		if (modelInfo == null) {
			val id = promptI18n(ModelI18n.PromptId())
			if (id.isBlank()) {
				invalidValue(); return@flow
			}
			val contextWindow: Int =
				promptI18n(ModelI18n.PromptContextWindow()).toIntOrNull()
					?: run { invalidValue(); return@flow }
			val maxOutputTokens =
				promptI18n(ModelI18n.PromptMaxOutputTokens()).toIntOrNull()
					?: run { invalidValue(); return@flow }
			val price =
				promptTokenPrice()
					?: run { invalidValue(); return@flow }
			
			suspend fun promptFeature(featureI18n: I18nDef) =
				promptYesOrNo(ModelI18n.PromptSetFeature(), i18n(featureI18n))
			
			val supportsStreaming =
				promptFeature(ModelFeature.StreamingFeature())
					?: run { invalidValue(); return@flow }
			val supportsToolCalls =
				promptFeature(ModelFeature.ToolCallFeature())
					?: run { invalidValue(); return@flow }
			val supportsReasoning =
				promptFeature(ModelFeature.ReasoningFeature())
					?: run { invalidValue(); return@flow }
			val supportsImage =
				promptFeature(ModelFeature.ImageFeature())
					?: run { invalidValue(); return@flow }
			val supportsJsonOutput =
				promptFeature(ModelFeature.JsonOutputFeature())
					?: run { invalidValue(); return@flow }
			
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
		
		emitDone()
	}
	
	private suspend fun promptTokenPrice(): ModelData.TokenPrice? {
		val inputPrice = if (promptYesOrNo(ModelI18n.PromptSetInputPrice()) ?: return null) {
			promptPriceTierList() ?: return null
		} else emptyList()
		
		val outputPrice = if (promptYesOrNo(ModelI18n.PromptSetOutputPrice()) ?: return null) {
			promptPriceTierList() ?: return null
		} else emptyList()
		
		return ModelData.TokenPrice(inputPrice, outputPrice)
	}
	
	private suspend fun promptPriceTierList(): List<PriceTier>? {
		val priceList = mutableListOf<PriceTier>()
		priceList.add(promptPriceTier() ?: return null)
		while (true) {
			if (!(promptYesOrNo(ModelI18n.PromptSetPrice()) ?: return null)) break
			priceList.add(promptPriceTier() ?: return null)
		}
		return priceList.toList()
	}
	
	private suspend fun promptPriceTier(): PriceTier? {
		val tieredPrice: Boolean = promptYesOrNo(ModelI18n.PromptTieredPrice()) ?: return null
		val fromTokens: Int
		val toTokens: Int?
		if (tieredPrice) {
			val result = promptI18n(ModelI18n.PromptPriceRange()).split("-", limit = 2).map { it.trim() }
			fromTokens = result[0].toIntOrNull() ?: return null
			val rawTo = result.getOrNull(1)
			toTokens = if (rawTo == null) rawTo else rawTo.toIntOrNull() ?: return null
		} else {
			fromTokens = 0
			toTokens = null
		}
		val price = promptPrice() ?: return null
		val cachedPrice: Price? = if (promptYesOrNo(ModelI18n.PromptSetCachedPrice()) ?: return null) {
			promptPrice()
		} else null
		return PriceTier(fromTokens, toTokens, price, cachedPrice)
	}
	
	private suspend fun promptPrice(): Price? {
		val tokenUnit = promptI18n(ModelI18n.PromptTokenUnit()).toIntOrNull() ?: return null
		val currency =
			trace.catching { Currency.getInstance(promptI18n(ModelI18n.PromptPriceCurrency()).uppercase()) }.getOrNull()
				?: return null
		val price = trace.catching {
			BigDecimal(
				promptI18n(ModelI18n.PromptPrice(), currency.getDisplayName(i18n.getLanguage()))
			)
		}.getOrNull() ?: return null
		return Price(price, currency, tokenUnit)
	}
	
	private suspend fun promptI18n(key: I18nDef, vararg args: Any): String =
		prompt(i18n(key).format(*args), true)
	
	
	private suspend fun promptYesOrNo(key: I18nDef, vararg args: Any): Boolean? {
		val result = prompt(
			i18n(key).format(*args), true
		)
		return when (result) {
			"y", "yes" -> true
			"n", "no", "" -> false
			else -> null
		}
	}
	
	//endregion
}
