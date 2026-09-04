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

package io.github.autotweaker.toolgen

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
	fileExtension = "toolgen.kts",
	compilationConfiguration = ToolgenScriptConfiguration::class,
)
abstract class ToolgenScript

object ToolgenScriptConfiguration : ScriptCompilationConfiguration({
	jvm {
		dependenciesFromClassloader(wholeClasspath = true, classLoader = ToolgenScript::class.java.classLoader)
	}
}) {
	@Suppress("unused")
	private fun readResolve(): Any = ToolgenScriptConfiguration
}
