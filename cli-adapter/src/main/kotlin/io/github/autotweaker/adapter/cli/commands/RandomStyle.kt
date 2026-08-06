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

package io.github.autotweaker.adapter.cli.commands

import kotlin.random.Random

private val colors: List<StyleBuilder.(Boolean) -> Unit> = listOf(
	StyleBuilder::black, StyleBuilder::red, StyleBuilder::green, StyleBuilder::yellow,
	StyleBuilder::blue, StyleBuilder::magenta, StyleBuilder::cyan, StyleBuilder::white,
)

fun StyleBuilder.randomStyle() {
	colors.random()(false)
	colors.random()(true)
	if (Random.nextBoolean()) bold()
	if (Random.nextBoolean()) dim()
	if (Random.nextBoolean()) italic()
	if (Random.nextBoolean()) underline()
}
