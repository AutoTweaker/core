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
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.Syntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n

@AutoService(Command::class)
class Version : Command {
	override val name = "version"
	override val description = i18n(VersionI18n.Desc())
	override val syntax = Syntax.EMPTY
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		out(core.appVersion.toString())
		done()
	}
}
