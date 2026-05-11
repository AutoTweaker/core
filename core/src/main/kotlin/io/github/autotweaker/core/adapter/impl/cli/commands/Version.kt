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

package io.github.autotweaker.core.adapter.impl.cli.commands

import com.google.auto.service.AutoService
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import io.github.autotweaker.core.adapter.impl.cli.Command.Param
import io.github.autotweaker.core.adapter.impl.cli.ParsedRequest
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@AutoService(Command::class)
class Version : Command {
	override val name = "version"
	override val description get() = I18n.get("cmd.version.desc")
	override val params = emptyList<Param>()
	private var coreVersion: SemVer = SemVer.parse("0.0.0")
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.coreVersion = coreVersion
	}
	
	override fun handle(request: ParsedRequest, prompt: suspend (String) -> String): Flow<Chunk> = flowOf(
		Chunk.Data(coreVersion.toString()),
	)
}