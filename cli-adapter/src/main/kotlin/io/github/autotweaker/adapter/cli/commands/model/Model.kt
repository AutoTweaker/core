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
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.SPACE
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.types.exception.notfound.ModelNotFoundException
import io.github.autotweaker.api.types.llm.ModelData
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
			all {
				positional("provider", ModelI18n.ParamProvider())
				positional("model", ModelI18n.ParamName())
			}
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
			val model = core.config.getModel(findModel(core)) ?: done(1)
			out(ModelI18n.ModelName(), model.displayName)
			out(ModelI18n.ProviderName(), getPositional(0))
			printModelInfo(model.modelInfo)
			
		}
		
		handleFlag("set-default") {
			core.config.setDefaultModel(findModel(core))
		}
		
		handleFlag("get-default") {
			core.config.getDefaultModel()?.let { printModel(it, core) } ?: out(ModelI18n.NotSet())
		}
		
		handleFlag("reset-default") {
			core.config.setDefaultModel(null)
		}
		
		done(1)
	}
	
	private suspend fun Console.list(core: CoreAPI) {
		core.config.listModels().forEach { model ->
			printModel(model.id, core)
		}
	}
	
	companion object : I18nable {
		suspend fun Console.findModel(core: CoreAPI) =
			findModel(core, getPositional(0), getPositional(1))
		
		suspend fun Console.findModel(core: CoreAPI, provider: String, model: String): UUID {
			val providerId = core.config.listProviders().find { it.displayName == provider }?.id
				?: error(ModelI18n.ProviderNotFound(), provider)
			val modelId = core.config.listModels()
				.find { it.providerId == providerId && it.displayName == model }?.id
				?: error(ModelI18n.ModelNotFound(), model)
			return modelId
		}
		
		suspend fun Console.printModel(id: UUID, core: CoreAPI) {
			val model = core.config.getModel(id) ?: throw ModelNotFoundException(id)
			val providerName = core.config.getProvider(model.providerId)?.displayName ?: i18n(ModelI18n.Unknown())
			out("[$providerName] ${model.displayName}")
		}
		
		suspend fun Console.printModelInfo(info: ModelData.ModelInfo) {
			val feature = buildList {
				if (info.supportsStreaming) add(ModelFeature.StreamingFeature())
				if (info.supportsToolCalls) add(ModelFeature.ToolCallFeature())
				if (info.supportsReasoning) add(ModelFeature.ReasoningFeature())
				if (info.supportsImage) add(ModelFeature.ImageFeature())
				if (info.supportsJsonOutput) add(ModelFeature.JsonOutputFeature())
			}.joinToString(separator = SPACE.toString()) { "[${i18n(it)}]" }
			
			out(ModelI18n.ModelId(), info.modelId)
			out(ModelI18n.ContextWindow(), formatUnit(info.contextWindow))
			out(ModelI18n.MaxOutput(), formatUnit(info.maxOutputTokens))
			out(ModelI18n.ModelFeature(), feature)
		}
		
		private fun formatUnit(number: Int): String = when {
			number == 0 -> 0
			number % 1_000_000 == 0 -> "${number / 1_000_000}m"
			number % 1_000 == 0 -> "${number / 1_000}k"
			else -> number
		}.toString()
	}
}
