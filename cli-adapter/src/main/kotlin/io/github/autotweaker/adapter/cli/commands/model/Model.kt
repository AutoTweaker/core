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
import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Model : Command {
	lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18n.i18nService
	
	override val name = "model"
	override val description get() = i18n.get(ModelI18n.Description())
	override val syntax
		get() = Syntax.xor(
			Syntax.leaf(i18n, Param.Type.FLAG, "list", ModelI18n.ParamList()),
			Syntax.all(
				Syntax.leaf(i18n, Param.Type.FLAG, "add", ModelI18n.ParamAdd()),
				Syntax.leaf(i18n, Param.Type.VALUE, "name", ModelI18n.ParamName()),
				Syntax.leaf(i18n, Param.Type.VALUE, "provider", ModelI18n.ParamProvider()),
				Syntax.leaf(i18n, Param.Type.VALUE, "info", ModelI18n.ParamAddInfo(), required = false),
			),
			Syntax.leaf(i18n, Param.Type.VALUE, "add-all", ModelI18n.ParamAddAll(), aliases = emptyList()),
			Syntax.all(
				Syntax.leaf(i18n, Param.Type.FLAG, "remove", ModelI18n.ParamRemove(), aliases = listOf("rm")),
				Syntax.leaf(i18n, Param.Type.POSITIONAL, "provider", ModelI18n.ParamProvider()),
				Syntax.leaf(i18n, Param.Type.POSITIONAL, "model", ModelI18n.ParamName()),
			)
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
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
			val provider = request.positional[0]
			val model = request.positional[1]
			emitAll(remove(provider, model))
			return@flow
		}
		
		emitDone(1)
	}
	
	fun list(): Flow<CmdOutput> = flow {
		val provider = core.config.listProviders()
		core.config.listModels().forEach { model ->
			val providerName =
				provider.find { it.id == model.data.providerId }?.displayName ?: i18n.get(ModelI18n.Unknown())
			emit(CmdOutput.Data("[$providerName] ${model.data.displayName}"))
		}
		emitDone()
	}
	
	fun remove(provider: String, model: String): Flow<CmdOutput> = flow {
		val providerId = core.config.listProviders().find { it.displayName == provider }?.id ?: run {
			emitI18n(i18n, ModelI18n.ProviderNotFound(), provider, error = true)
			emitDone(1)
			return@flow
		}
		val modelId =
			core.config.listModels().find { it.data.displayName == model && it.data.providerId == providerId }?.data?.id
				?: run {
					emitI18n(i18n, ModelI18n.ModelNotFound(), model, error = true)
					emitDone(1)
					return@flow
				}
		
		core.config.removeModel(modelId)
		emitDone()
	}
}
