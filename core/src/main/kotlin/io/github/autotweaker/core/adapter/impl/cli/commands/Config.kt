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
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Param
import io.github.autotweaker.core.adapter.impl.cli.Request
import io.github.autotweaker.core.adapter.impl.cli.Syntax
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow

@AutoService(Command::class)
class Config : Command {
	override val name: String = "cfg"
	override val description: String
		get() = I18n.get("cfg.desc")
	override val syntax = Syntax.xor(
		Syntax.all(
			Syntax.xor(
				Syntax.all(
					Syntax.leaf(Param.Flag("list", I18n.get("cfg.list"))),
					Syntax.leaf(Param.Value("number", I18n.get("cfg.number"))),
				),
				Syntax.all(
					Syntax.leaf(Param.Flag("search", I18n.get("cfg.search"))),
					Syntax.xor(
						Syntax.leaf(Param.Flag("key", I18n.get("cfg.search.key"))),
						Syntax.leaf(Param.Flag("value", I18n.get("cfg.search.value"))),
						Syntax.leaf(Param.Flag("desc", I18n.get("cfg.search.desc"))),
						required = false,
					),
				),
			),
			Syntax.leaf(Param.Value("number", I18n.get("cfg.number"))),
			Syntax.leaf(Param.Flag("all", I18n.get("cfg.all"))),
		), Syntax.all(
			//TODO
		)
	)
	
	override fun handle(
		request: Request, prompt: suspend (String) -> String
	): Flow<Command.Chunk> {
		TODO("Not yet implemented")
	}
}
