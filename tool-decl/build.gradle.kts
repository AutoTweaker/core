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

val toolgenHost = configurations.create("toolgenHost") {
	isCanBeConsumed = false
	isCanBeResolved = true
}
dependencies.add(toolgenHost.name, dependencies.project(":autotweaker-tool-gen"))

@CacheableTask
abstract class GenerateToolArgsTask : DefaultTask() {
	@get:Inject
	abstract val execOperations: ExecOperations
	
	@get:Classpath
	abstract val runtimeClasspath: ConfigurableFileCollection
	
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val scripts: ConfigurableFileCollection
	
	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty
	
	@TaskAction
	fun generate() {
		val target = outputDir.get().asFile
		target.deleteRecursively()
		val sources = scripts.files.sortedBy { it.name }
		if (sources.isEmpty()) return
		execOperations.javaexec {
			mainClass.set("io.github.autotweaker.toolgen.ToolgenScriptHostKt")
			classpath = runtimeClasspath
			args(target.absolutePath)
			sources.forEach { args(it.absolutePath) }
		}
	}
}

val generateToolArgs = tasks.register<GenerateToolArgsTask>("generateToolArgs") {
	group = "generate"
	description = "Runs toolgen declaration scripts and generates ToolArgs sources"
	runtimeClasspath.from(toolgenHost)
	outputDir.set(layout.buildDirectory.dir("generated/args"))
	scripts.setFrom(
		layout.projectDirectory.dir("src/main/tools").asFileTree.matching { include("*.toolgen.kts") }
	)
}

listOf(
	"generatedApiArgs" to "io/github/autotweaker/api",
	"generatedCoreMeta" to "io/github/autotweaker/core",
).forEach { (name, packagePath) ->
	configurations.create(name) {
		isCanBeConsumed = true
		isCanBeResolved = false
		outgoing.artifact(layout.buildDirectory.dir("generated/args/$packagePath")) {
			builtBy(generateToolArgs)
		}
	}
}
