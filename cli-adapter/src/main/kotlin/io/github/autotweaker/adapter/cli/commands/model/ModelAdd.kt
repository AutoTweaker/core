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
import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.llm.ModelData

class ModelAdd(
	private val core: CoreAPI
) : Traceable {
	suspend fun Console.addAll(providerName: String): Nothing {
		val provider = core.config.listProviders().find { it.displayName == providerName }
			?: error(ModelI18n.ProviderNotFound(), providerName)
		val providerMeta = core.config.getProviderMeta(provider.providerType)
		val modelList =
			core.config.listModels().filter { it.providerId == provider.id }.map { it.displayName }
		
		providerMeta.models.map { it }.forEach {
			if (it.modelId !in modelList) core.config.setModel(
				ModelData(
					id = UUID(), displayName = it.modelId, modelInfo = it, providerId = provider.id
				)
			)
		}
		done()
	}
	
	suspend fun Console.add(name: String, provider: String, infoId: String?): Nothing {
		val provider = core.config.listProviders().find { it.displayName == provider }
			?: error(ModelI18n.ProviderNotFound(), provider)
		
		var modelInfo: ModelData.ModelInfo? = null
		core.config.getProviderMeta(provider.providerType).models.find { it.modelId == infoId }?.let { modelInfo = it }
		
		if (core.config.listModels().any { it.displayName == name && it.providerId == provider.id })
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
				supportsStreaming = supportsStreaming,
				supportsToolCalls = supportsToolCalls,
				supportsReasoning = supportsReasoning,
				supportsImage = supportsImage,
				supportsJsonOutput = supportsJsonOutput
			)
		}
		
		core.config.setModel(
			ModelData(
				id = UUID(),
				displayName = name,
				modelInfo = modelInfo,
				providerId = provider.id
			)
		)
		
		done()
	}
	
	private suspend fun Console.invalidValue(): Nothing = error(ModelI18n.InvalidValue())
}
