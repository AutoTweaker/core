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

package io.github.autotweaker.plugin.sdk

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.jvm.tasks.ProcessResources

@Suppress("unused")
class AutoTweakerPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		val version = sdkVersion()
		project.pluginManager.withPlugin("java") {
			val provided = ProvidedDependencies.load()
			project.logger.lifecycle(
				"AutoTweaker Plugin SDK {} loaded {} provided dependencies",
				version,
				provided.size
			)
			alignProvidedVersions(project, provided)
			project.dependencies.add("compileOnly", "io.github.autotweaker:api:$version")
			injectApiVersion(project, version)
			configureShading(project, provided)
			configureToolgen(project, version)
		}
	}
	
	private fun configureToolgen(project: Project, version: String) {
		val scripts = project.layout.projectDirectory.dir("src/main/tools")
			.asFileTree.matching { it.include("*.toolgen.kts") }
		val toolgenClasspath = project.configurations.register("toolgenClasspath") { configuration ->
			configuration.isCanBeConsumed = false
			configuration.isCanBeResolved = true
			configuration.defaultDependencies { dependencies ->
				dependencies.add(project.dependencies.create("io.github.autotweaker:tool-gen:$version"))
			}
		}
		val generatedDir = project.layout.buildDirectory.dir("generated/toolgen/args")
		val generate = project.tasks.register("generateToolArgs", GenerateToolArgsTask::class.java) { task ->
			task.group = "generate"
			task.description = "Runs toolgen declaration scripts and generates ToolArgs sources"
			task.scripts.setFrom(scripts)
			task.outputDir.set(generatedDir)
			task.toolgenClasspath.from(toolgenClasspath)
			task.onlyIf { scripts.files.isNotEmpty() }
		}
		project.extensions.getByType(SourceSetContainer::class.java).named("main") { sourceSet ->
			sourceSet.java.srcDir(generate)
		}
	}
	
	private fun alignProvidedVersions(project: Project, provided: Map<String, String>) {
		project.configurations.configureEach { configuration ->
			configuration.resolutionStrategy.eachDependency { details ->
				val key = "${details.requested.group}:${details.requested.name}"
				val coreVersion = provided[key]
				if (coreVersion != null) {
					if (details.requested.version != coreVersion) {
						project.logger.warn(
							"Dependency {} was requested with version {} but is aligned to version {} provided by AutoTweaker core",
							key, details.requested.version, coreVersion
						)
					}
					details.useVersion(coreVersion)
					details.because("provided by AutoTweaker core")
				}
			}
		}
	}
	
	private fun injectApiVersion(project: Project, version: String) {
		val outputDir = project.layout.buildDirectory.dir("generated/plugin-metadata")
		val generateTask = project.tasks.register("generatePluginMetadata") { task ->
			val target = outputDir.map { it.file("META-INF/autotweaker/plugin.properties") }
			task.inputs.property("apiVersion", version)
			task.outputs.file(target)
			task.doLast {
				target.get().asFile.apply {
					parentFile.mkdirs()
					writeText("apiVersion=$version")
				}
			}
		}
		project.tasks.named("processResources", ProcessResources::class.java).configure { processResources ->
			processResources.dependsOn(generateTask)
			processResources.from(outputDir) { spec ->
				spec.include("META-INF/autotweaker/plugin.properties")
			}
		}
	}
	
	private fun configureShading(project: Project, provided: Map<String, String>) {
		project.pluginManager.apply("com.gradleup.shadow")
		project.tasks.named("jar", Jar::class.java) { jar ->
			jar.enabled = false
		}
		project.configurations.named("apiElements") { configuration ->
			configuration.outgoing.artifacts.clear()
			configuration.outgoing.variants.clear()
			configuration.outgoing.artifact(project.tasks.named("shadowJar"))
		}
		project.configurations.named("runtimeElements") { configuration ->
			configuration.outgoing.artifacts.clear()
			configuration.outgoing.variants.clear()
			configuration.outgoing.artifact(project.tasks.named("shadowJar"))
		}
		val relocate = project.providers.gradleProperty("autotweaker.relocate").getOrElse("true") != "false"
		project.tasks.named("shadowJar", ShadowJar::class.java) { shadowJar ->
			shadowJar.archiveClassifier.set("")
			shadowJar.enableAutoRelocation.set(relocate)
			shadowJar.relocationPrefix.set(relocationPrefix(project))
			shadowJar.mergeServiceFiles()
			shadowJar.filesMatching("META-INF/services/**") { details ->
				details.duplicatesStrategy = DuplicatesStrategy.INCLUDE
			}
			shadowJar.dependencies { filter ->
				provided.keys.forEach { filter.exclude(filter.dependency(it)) }
			}
		}
	}
	
	private fun relocationPrefix(project: Project): String {
		val group = project.group.toString()
		val identifier = if (group.isEmpty()) project.name else "$group.${project.name}"
		return "autotweaker.shaded." + identifier.replace('-', '_')
	}
	
	private fun sdkVersion(): String {
		val stream = javaClass.classLoader
			.getResourceAsStream("io/github/autotweaker/plugin/sdk/sdk.properties")!!
		return stream.use { it.reader().readText() }
			.lineSequence()
			.first { it.startsWith("version=") }
			.substringAfter('=')
	}
}
