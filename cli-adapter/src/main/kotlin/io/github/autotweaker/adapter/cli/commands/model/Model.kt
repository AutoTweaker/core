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
import io.github.autotweaker.adapter.cli.commands.*
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.*

@AutoService(Command::class)
class Model : Command, I18nable, Traceable {
	lateinit var core: CoreAPI
	
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
				flag("set-default", ModelI18n.ParamDefault()) { aliases() }
			}
			positional("provider", ModelI18n.ParamProvider())
			positional("model", ModelI18n.ParamName())
		}
	}
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		if (request.has("list")) {
			emitAll(list())
			return@flow
		}
		
		val add = ModelAdd(core, prompt)
		if (request.has("add-all")) {
			emitAll(add.addAll(request.get("add-all") ?: error("Missing provider name")))
			return@flow
		}
		
		if (request.has("add")) {
			val name: String = request.get("name") ?: error("Missing model name")
			val provider: String = request.get("provider") ?: error("Missing provider name")
			val info: String? = request.get("info")
			emitAll(add.add(name, provider, info))
			return@flow
		}
		
		if (request.has("remove")) {
			core.config.removeModel(findModel(request, core) ?: return@flow)
			emitDone()
			return@flow
		}
		
		if (request.has("set-default")) {
			core.config.setDefaultModel(findModel(request, core) ?: return@flow)
			emitDone()
			return@flow
		}
		
		emitDone(1)
	}
	
	private fun list(): Flow<CmdOutput> = flow {
		val provider = core.config.listProviders()
		core.config.listModels().forEach { model ->
			val providerName =
				provider.find { it.id == model.data.providerId }?.displayName ?: i18n(ModelI18n.Unknown())
			emit(CmdOutput.Data("[$providerName] ${model.data.displayName}"))
		}
		emitDone()
	}
	
	companion object : I18nable {
		suspend fun FlowCollector<CmdOutput>.findModel(request: Request, core: CoreAPI): UUID? {
			val provider = request.positional[0]
			val model = request.positional[1]
			val providerId = core.config.listProviders().find { it.displayName == provider }?.id ?: run {
				emitI18n(ModelI18n.ProviderNotFound(), provider, error = true)
				emitDone(1)
				return null
			}
			val modelId =
				core.config.listModels()
					.find { it.data.displayName == model && it.data.providerId == providerId }?.data?.id
					?: run {
						emitI18n(ModelI18n.ModelNotFound(), model, error = true)
						emitDone(1)
						return null
					}
			return modelId
		}
	}
}
