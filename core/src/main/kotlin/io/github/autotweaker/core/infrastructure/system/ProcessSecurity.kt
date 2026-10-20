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

package io.github.autotweaker.core.infrastructure.system

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout

object ProcessSecurity {
	private const val PR_SET_DUMPABLE = 4
	
	fun disablePtrace() {
		val linker = Linker.nativeLinker()
		val prctl = linker.downcallHandle(
			linker.defaultLookup().find("prctl").orElseThrow(),
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.JAVA_INT,
				ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_LONG,
			)
		)
		val result = prctl.invokeWithArguments(PR_SET_DUMPABLE, 0L, 0L, 0L, 0L) as Int
		if (result != 0) error("prctl(PR_SET_DUMPABLE) failed")
	}
}
