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
import com.github.difflib.UnifiedDiffUtils

fun unifiedDiff(oldContent: String?, newContent: String): String? {
	val oldLines = oldContent?.takeIf { it.isNotEmpty() }?.lines().orEmpty()
	val newLines = newContent.takeIf { it.isNotEmpty() }?.lines().orEmpty()
	val patch = DiffUtils.diff(oldLines, newLines)
	val originalFileName = if (oldLines.isEmpty()) null else ""
	return UnifiedDiffUtils.generateUnifiedDiff(originalFileName, "", oldLines, patch, 3)
		.drop(2)
		.takeIf { it.isNotEmpty() }
		?.joinToString("\n")
}
