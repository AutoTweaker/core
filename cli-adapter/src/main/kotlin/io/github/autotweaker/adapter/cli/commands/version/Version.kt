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

package io.github.autotweaker.adapter.cli.commands.version

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.Command
import io.github.autotweaker.adapter.cli.Request
import io.github.autotweaker.adapter.cli.Syntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@AutoService(Command::class)
class Version : Command {
	override val name = "version"
	override val description get() = i18n.get(VersionI18n.Desc())
	override val syntax = Syntax.none()
	private var coreVersion: SemVer = SemVer.parse("0.0.0")
	private lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18nService()
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
		this.coreVersion = coreVersion
	}
	
	override fun handle(
		request: Request,
		prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> =
		flowOf(
			CmdOutput.Data(coreVersion.toString()),
			CmdOutput.Done(),
		)
}