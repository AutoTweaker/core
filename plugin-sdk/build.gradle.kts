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
	id("org.jetbrains.dokka")
	`maven-publish`
	signing
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
dependencies.add(
	coreRuntimeClasspath.name,
	dependencies.project(path = ":autotweaker-core", configuration = "runtimeElements")
)

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

java {
	withSourcesJar()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
	description = "将 Dokka 生成的文档打包为 javadoc jar"
	from(tasks.named("dokkaGeneratePublicationHtml"))
	archiveClassifier.set("javadoc")
}

publishing {
	publications.withType<MavenPublication>().configureEach {
		if (!name.endsWith("PluginMarkerMaven")) {
			artifact(javadocJar)
		}
		pom {
			name.set("AutoTweaker Plugin SDK")
			description.set("Build toolkit for AutoTweaker plugins")
			url.set("https://github.com/AutoTweaker/core")
			licenses {
				license {
					name.set("GNU General Public License v3.0 or later")
					url.set("https://www.gnu.org/licenses/gpl-3.0.html")
				}
			}
			developers {
				developer {
					id.set("WhiteElephant-abc")
					name.set("WhiteElephant-abc")
					url.set("https://github.com/WhiteElephant-abc")
				}
			}
			scm {
				connection.set("scm:git:git://github.com/AutoTweaker/core.git")
				developerConnection.set("scm:git:ssh://git@github.com/AutoTweaker/core.git")
				url.set("https://github.com/AutoTweaker/core")
			}
		}
	}
}

signing {
	val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
	if (signingKey != null) {
		useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingInMemoryKeyPassword").getOrElse(""))
		publishing.publications.configureEach { sign(this) }
	}
}
