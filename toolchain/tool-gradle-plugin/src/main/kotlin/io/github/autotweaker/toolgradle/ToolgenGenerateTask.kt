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

package io.github.autotweaker.toolgradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class ToolgenGenerateTask : DefaultTask() {
	@get:Inject
	protected abstract val execOperations: ExecOperations
	
	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty
	
	@get:InputFiles
	abstract val scripts: ConfigurableFileCollection
	
	@get:Classpath
	abstract val classpath: ConfigurableFileCollection
	
	@get:Internal
	abstract val scriptsDir: DirectoryProperty
	
	@TaskAction
	fun generate() {
		execOperations.javaexec { spec ->
			spec.mainClass.set("io.github.autotweaker.toolgen.ToolgenScriptHostKt")
			spec.classpath = classpath
			spec.args(outputDir.get().asFile.absolutePath)
			spec.args(scriptsDir.get().asFile.absolutePath)
		}
	}
}
