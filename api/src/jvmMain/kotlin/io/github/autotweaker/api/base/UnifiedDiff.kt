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

package io.github.autotweaker.api.base

import com.github.difflib.DiffUtils

fun unifiedDiff(oldContent: String?, newContent: String): String? {
	val oldLines = oldContent?.takeIf { it.isNotEmpty() }?.lines().orEmpty()
	val newLines = newContent.takeIf { it.isNotEmpty() }?.lines().orEmpty()
	val deltas = DiffUtils.diff(oldLines, newLines).getDeltas()
	if (deltas.isEmpty()) return null
	return deltas.joinToString("\n") { delta ->
		val source = delta.source
		val target = delta.target
		val oldStart = if (oldLines.isEmpty()) 0 else source.position + 1
		val newStart = target.position + 1
		buildString {
			append("@@ -")
			append(oldStart)
			if (source.lines.size != 1) {
				append(',')
				append(source.lines.size)
			}
			append(" +")
			append(newStart)
			if (target.lines.size != 1) {
				append(',')
				append(target.lines.size)
			}
			append(" @@")
			source.lines.forEach {
				append('\n')
				append('-')
				append(it)
			}
			target.lines.forEach {
				append('\n')
				append('+')
				append(it)
			}
		}
	}
}
