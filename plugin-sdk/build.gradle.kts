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

plugins {
	kotlin("jvm")
	`java-gradle-plugin`
	`maven-publish`
}

repositories {
	mavenCentral()
}

kotlin {
	jvmToolchain(25)
}

dependencies {
	implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
}

gradlePlugin {
	plugins {
		create("autotweakerPlugin") {
			id = "io.github.autotweaker.plugin.sdk"
			implementationClass = "io.github.autotweaker.plugin.sdk.AutoTweakerPlugin"
			displayName = "AutoTweaker Plugin SDK"
			description = "Build toolkit for AutoTweaker third-party plugins"
		}
	}
}

abstract class GenerateResourceFile : DefaultTask() {
	@get:Input
	abstract val content: Property<String>
	
	@get:Input
	abstract val resourcePath: Property<String>
	
	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty
	
	@TaskAction
	fun write() {
		val root = outputDir.get().asFile.apply {
			deleteRecursively()
			mkdirs()
		}
		File(root, resourcePath.get()).apply {
			parentFile.mkdirs()
			writeText(content.get())
		}
	}
}

val sdkResourcesDir = layout.buildDirectory.dir("generated/sdk/resources")
val sdkVersion = project.version.toString()

val generateSdkProperties = tasks.register<GenerateResourceFile>("generateSdkProperties") {
	description = "生成 sdk.properties，内含本插件 SDK 的版本号"
	content.set("version=$sdkVersion")
	resourcePath.set("io/github/autotweaker/plugin/sdk/sdk.properties")
	outputDir.set(sdkResourcesDir)
}

sourceSets["main"].resources.srcDir(files(sdkResourcesDir).builtBy(generateSdkProperties))

val coreRuntimeClasspath = configurations.register("coreRuntimeClasspath") {
	isCanBeConsumed = false
	isCanBeResolved = true
}
dependencies.add(coreRuntimeClasspath.name, dependencies.project(path = ":core", configuration = "runtimeElements"))

val providedDependencies = coreRuntimeClasspath.map { configuration ->
	val result = configuration.incoming.resolutionResult
	val rootId = result.root.id
	result.allComponents
		.asSequence()
		.filter { it.id != rootId }
		.mapNotNull { it.moduleVersion }
		.map { "${it.group}:${it.name}:${it.version}" }
		.sorted()
		.toList()
}

val providedResourcesDir = layout.buildDirectory.dir("generated/provided/resources")

val generateProvidedDependencies = tasks.register<GenerateResourceFile>("generateProvidedDependencies") {
	description = "导出 core 运行时依赖清单，供插件构建时判定打包策略"
	content.set(providedDependencies.map { it.joinToString("\n") })
	resourcePath.set("io/github/autotweaker/plugin/sdk/provided-dependencies.txt")
	outputDir.set(providedResourcesDir)
}

sourceSets["main"].resources.srcDir(files(providedResourcesDir).builtBy(generateProvidedDependencies))

publishing {
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/AutoTweaker/core")
			credentials {
				username = providers.gradleProperty("gpr.user").getOrElse("")
				password = providers.gradleProperty("gpr.key").getOrElse("")
			}
		}
	}
}
