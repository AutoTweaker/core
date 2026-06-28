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

package io.github.autotweaker.api

/**
 * 遍历对象并调用 [action]，在每两次遍历中间调用 [between]
 */
inline fun <T> Iterable<T>.forEachBetween(
	action: (T) -> Unit,
	between: () -> Unit,
) {
	val iterator = iterator()
	if (!iterator.hasNext()) return
	
	action(iterator.next())
	
	while (iterator.hasNext()) {
		between()
		action(iterator.next())
	}
}
