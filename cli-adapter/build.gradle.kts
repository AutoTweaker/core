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
	implementation(project(":api"))
	implementation(project(":cli-protocol"))
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("io.ktor:ktor-network:3.5.1")
	implementation("org.slf4j:slf4j-api:2.0.18")
	
	testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
	testImplementation(kotlin("test"))
	testImplementation("io.mockk:mockk:1.14.11")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

afterEvaluate {
	extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KaptExtensionConfig>().javacOptions {
		option("-nowarn")
	}
}

val inDocker = System.getenv("DOCKER_TEST") == "true"

if (inDocker) {
	tasks.test {
		useJUnitPlatform()
		jvmArgs(
			"-Dnet.bytebuddy.experimental=true",
			"--add-opens", "java.base/java.util=ALL-UNNAMED",
			"--add-opens", "java.base/java.lang=ALL-UNNAMED",
			"--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
		)
	}
} else {
	tasks.test { enabled = false }
}

evaluationDependsOn(":cli-protocol")

tasks.jar {
	dependsOn(project(":cli-protocol").tasks.named("jvmJar"))
	from(zipTree(project(":cli-protocol").tasks.named("jvmJar").get().outputs.files.singleFile))
}
