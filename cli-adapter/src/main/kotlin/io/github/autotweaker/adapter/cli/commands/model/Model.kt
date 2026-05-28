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
import io.github.autotweaker.adapter.cli.commands.version.VersionI18n
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Model : Command {
	lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18n.i18nService
	
	override val name = "model"
	override val description get() = i18n.get(VersionI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.leaf(Param.Flag("list", "none")),
			Syntax.all(
				Syntax.leaf(Param.Flag("add", "none")),
				Syntax.leaf(Param.Flag("name", "none")),
				Syntax.leaf(Param.Value("provider", "none")),
				Syntax.leaf(Param.Value("type", "none"))
			)
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
	
	}
}
