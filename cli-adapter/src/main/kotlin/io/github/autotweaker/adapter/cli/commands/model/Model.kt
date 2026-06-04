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
			Syntax.leaf(Param.Flag("list", i18n.get(ModelI18n.ParamList())), required = true),
			Syntax.all(
				Syntax.leaf(Param.Flag("add", i18n.get(ModelI18n.ParamAdd())), required = true),
				Syntax.leaf(Param.Value("name", i18n.get(ModelI18n.ParamAddName())), required = true),
				Syntax.leaf(Param.Value("provider", i18n.get(ModelI18n.ParamAddProvider())), required = true),
				Syntax.leaf(Param.Value("info", i18n.get(ModelI18n.ParamAddInfo())))
			),
			Syntax.leaf(
				Param.Value("add-all", i18n.get(ModelI18n.ParamAddAll()), aliases = emptyList()),
				required = true
			),
			
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
}
