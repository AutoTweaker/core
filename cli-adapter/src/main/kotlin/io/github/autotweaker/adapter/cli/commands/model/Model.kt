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
			Syntax.leaf(Param.Flag("list", "none")), Syntax.all(
				Syntax.leaf(Param.Flag("add", "none")),
				Syntax.leaf(Param.Value("name", "none")),
				Syntax.leaf(Param.Value("provider", "none")),
				Syntax.leaf(Param.Value("id", "none"))
			), Syntax.leaf(Param.Value("add-all", "none", aliases = emptyList()))
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val add = ModelAdd(core, prompt)
		if (request.has("add-all")) {
			emitAll(add.addAll(request.get("add-all") ?: error("Missing provider name")))
			return@flow
		}
	}
}
