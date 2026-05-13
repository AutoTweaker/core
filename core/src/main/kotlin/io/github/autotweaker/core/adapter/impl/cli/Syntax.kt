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

package io.github.autotweaker.core.adapter.impl.cli

sealed class Syntax {
	data class All(val children: List<Syntax>, val required: Boolean = true) : Syntax()
	data class Xor(val children: List<Syntax>, val required: Boolean = true) : Syntax()
	data class Leaf(val param: Param, val required: Boolean = false) : Syntax()
	
	companion object {
		fun all(vararg children: Syntax, required: Boolean = true) = All(children.toList(), required)
		fun xor(vararg children: Syntax, required: Boolean = true) = Xor(children.toList(), required)
		fun leaf(param: Param, required: Boolean = false) = Leaf(param, required)
		fun none() = All(emptyList(), required = false)
	}
}
