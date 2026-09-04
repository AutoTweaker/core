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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

@Suppress("unused")
@OptIn(ExperimentalKotlinGradlePluginApi::class)
class ToolgenPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		val extension = project.extensions.create("toolgen", ToolgenExtension::class.java).apply {
			scriptsDirectory.convention(project.layout.projectDirectory.dir("src/main/tools"))
			outputDirectory.convention(project.layout.buildDirectory.dir("generated/toolgen"))
			attachToSourceSet.convention(true)
		}
		
		val scriptHost = project.configurations.create("toolgenScriptHost") { conf ->
			conf.isCanBeConsumed = false
			conf.isCanBeResolved = true
		}
		project.dependencies.add(scriptHost.name, "io.github.autotweaker:tool-gen:${toolgenVersion()}")
		
		val generateTask = project.tasks.register("generateToolArgs", ToolgenGenerateTask::class.java) { task ->
			task.group = "generate"
			task.description = "Runs toolgen declaration scripts and generates ToolArgs sources"
			task.outputDir.set(extension.outputDirectory)
			task.scriptsDir.set(extension.scriptsDirectory)
			task.scripts.from(extension.scriptsDirectory.map { dir ->
				dir.asFileTree.matching { pattern -> pattern.include("*.toolgen.kts") }
			})
			task.classpath.from(scriptHost)
			task.onlyIf { task.scripts.files.isNotEmpty() }
		}
		
		project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
			project.afterEvaluate {
				if (extension.attachToSourceSet.get()) {
					val kotlin = project.extensions.getByType(
						org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java
					)
					kotlin.sourceSets.getByName("main").generatedKotlin.srcDir(generateTask.flatMap { it.outputDir })
				}
			}
		}
	}
	
	private fun toolgenVersion(): String {
		val stream = javaClass.classLoader
			.getResourceAsStream("io/github/autotweaker/toolgradle/toolgen.properties")
			?: error("toolgen.properties not found in tool-gradle-plugin jar; run processResources first")
		return stream.use { it.reader().readText() }
			.lineSequence()
			.first { it.startsWith("toolgen=") }
			.substringAfter('=')
	}
}
