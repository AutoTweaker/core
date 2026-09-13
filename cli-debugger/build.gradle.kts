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
}

dependencies {
	implementation(project(":api"))
	implementation(project(":cli-adapter"))
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
	implementation("org.slf4j:slf4j-api:2.0.18")
	kapt("com.google.auto.service:auto-service:1.1.1")
}
afterEvaluate {
	extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KaptExtensionConfig>().javacOptions {
		option("-nowarn")
	}
}

val pluginMetadataDir = layout.buildDirectory.dir("generated/plugin-metadata")

val generatePluginMetadata = tasks.register("generatePluginMetadata") {
	description = "生成 plugin.properties，内含本插件声明的 api 版本"
	val outputDir = pluginMetadataDir
	val apiVersion = version.toString()
	inputs.property("apiVersion", apiVersion)
	outputs.dir(outputDir)
	doLast {
		outputDir.get().file("META-INF/autotweaker/plugin.properties").asFile.apply {
			parentFile.mkdirs()
			writeText("apiVersion=$apiVersion")
		}
	}
}

tasks.processResources {
	dependsOn(generatePluginMetadata)
	from(pluginMetadataDir) { include("META-INF/autotweaker/plugin.properties") }
}

