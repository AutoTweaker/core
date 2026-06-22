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

package io.github.autotweaker.adapter.cli

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.i18n
import io.github.autotweaker.adapter.cli.Param.Type
import io.github.autotweaker.api.i18n.I18nDef

sealed class Syntax {
	abstract val required: Boolean
	
	data class All(val children: List<Syntax>, override val required: Boolean = true) : Syntax()
	data class Xor(val children: List<Syntax>, override val required: Boolean = true) : Syntax()
	data class Leaf(val param: Param, override val required: Boolean = true) : Syntax()
	
	companion object : I18nable {
		fun all(vararg children: Syntax, required: Boolean = true) = All(children.toList(), required)
		fun xor(vararg children: Syntax, required: Boolean = true) = Xor(children.toList(), required)
		fun none() = All(emptyList(), required = false)
		
		fun leaf(
						type: Type,
			name: String,
			desc: I18nDef,
			required: Boolean = true,
			aliases: List<String>,
		) = Leaf(
			Param.fromI18n(type, name, desc, aliases), required
		)
		
		fun leaf(
						type: Type,
			name: String,
			desc: I18nDef,
			required: Boolean = true,
		) = Leaf(
			Param.fromI18n(type, name, desc), required
		)
	}
}
