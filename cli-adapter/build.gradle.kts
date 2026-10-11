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
	kotlin("kapt")
	id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
	implementation(project(":autotweaker-api"))
	implementation(project(":cli-protocol"))
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("io.ktor:ktor-network:3.6.0")
	implementation("org.slf4j:slf4j-api:2.0.20")
	implementation("com.google.guava:guava:33.7.1-jre")
	implementation("com.ibm.icu:icu4j:78.3")
	implementation("io.github.java-diff-utils:java-diff-utils:4.17")
}

afterEvaluate {
	extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KaptExtensionConfig>().javacOptions {
		option("-nowarn")
	}
}

val exportRuntime = tasks.register<Sync>("exportRuntime") {
	description = "导出生产运行时产物集合（cli-adapter jar + 全部依赖 jar），供 AutoTweaker/test 仓库引用"
	from(tasks.named("jar"))
	from(configurations.named("runtimeClasspath")) { include("*.jar") }
	into(layout.buildDirectory.dir("runtime"))
}

val cliProtocolJar = configurations.register("cliProtocolJar") {
	isCanBeConsumed = false
	isCanBeResolved = true
	isTransitive = false
	attributes {
		attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "jvm")
	}
}
dependencies.add(cliProtocolJar.name, dependencies.project(path = ":cli-protocol", configuration = "jvmApiElements"))

tasks.jar {
	from(zipTree(cliProtocolJar.map { it.singleFile }))
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val pluginMetadataDir = layout.buildDirectory.dir("generated/plugin-metadata")

val generatePluginMetadata = tasks.register("generatePluginMetadata") {
	description = "生成 plugin.properties，内含本插件的 id、版本号与声明的 api 版本"
	val outputDir = pluginMetadataDir
	val pluginVersion = version.toString()
	inputs.property("pluginVersion", pluginVersion)
	inputs.property("apiVersion", pluginVersion)
	outputs.dir(outputDir)
	doLast {
		outputDir.get().file("META-INF/autotweaker/plugin.properties").asFile.apply {
			parentFile.mkdirs()
			writeText("id=io.github.autotweaker.adapter.cli\nversion=$pluginVersion\napiVersion=$pluginVersion")
		}
	}
}

tasks.processResources {
	dependsOn(generatePluginMetadata)
	from(pluginMetadataDir) { include("META-INF/autotweaker/plugin.properties") }
}
