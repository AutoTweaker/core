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

import java.time.Instant

plugins {
	kotlin("jvm")
	kotlin("kapt")
	id("org.jetbrains.kotlin.plugin.serialization")
	application
	jacoco
	
}

application {
	mainClass = "io.github.autotweaker.core.MainKt"
	applicationName = "autotweaker"
}

tasks.named<JavaExec>("run") {
	systemProperty("log.level", providers.systemProperty("log.level").orElse("DEBUG").get())
}

dependencies {
	implementation(project(":api"))
	
	implementation("io.ktor:ktor-client-core:3.5.0")
	implementation("io.ktor:ktor-client-java:3.5.0")
	implementation("io.ktor:ktor-client-cio:3.5.0")
	implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	
	
	testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
	testImplementation(kotlin("test"))
	testImplementation("io.mockk:mockk:1.14.9")
	testImplementation("io.ktor:ktor-client-mock:3.5.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("org.jetbrains.exposed:exposed-core:1.3.0")
	implementation("org.jetbrains.exposed:exposed-dao:1.3.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
	
	implementation("com.h2database:h2:2.4.240")
	
	implementation("org.slf4j:slf4j-api:2.0.18")
	implementation("ch.qos.logback:logback-classic:1.5.32")
	
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("com.fasterxml.jackson.core:jackson-core:2.21.3")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
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
		finalizedBy(tasks.jacocoTestReport)
	}
} else {
	tasks.test { enabled = false }
	tasks.check { dependsOn(rootProject.tasks.named("testInDocker")) }
	tasks.jacocoTestReport {
		dependsOn(rootProject.tasks.named("testInDocker"))
		reports {
			xml.required = true
			html.required = true
		}
	}
}

// region 生成版本资源文件

val generateVersionProperties by tasks.registering {
	description = "生成 version.properties（含 git hash 和构建时间戳）"
	notCompatibleWithConfigurationCache("访问 git 命令和环境变量")
	
	val outputDir = layout.buildDirectory.dir("generated/version")
	val outputFile = outputDir.map { it.file("version.properties") }
	
	outputs.file(outputFile)
	
	doLast {
		val gitHash = runCatching {
			val process =
				ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectError(ProcessBuilder.Redirect.DISCARD)
					.start()
			val output = process.inputStream.bufferedReader().readText().trim()
			if (process.waitFor() != 0) throw RuntimeException("git exited non-zero")
			output
		}.getOrDefault("unknown")
		
		val timestamp = Instant.now().toString().replace(":", "")
		
		val githubRef = System.getenv("GITHUB_REF") ?: ""
		val fullVersion = if (githubRef.startsWith("refs/tags/v")) {
			"${githubRef.removePrefix("refs/tags/v")}+$gitHash"
		} else {
			val baseVersion = project.version.toString()
			val stripped = baseVersion.replace(Regex("-[a-zA-Z].*"), "")
			"$stripped-dev+$timestamp.$gitHash"
		}
		
		outputFile.get().asFile.apply {
			parentFile.mkdirs()
			writeText("version=$fullVersion")
		}
	}
}

tasks.named<ProcessResources>("processResources") {
	dependsOn(generateVersionProperties)
	from(generateVersionProperties.map { it.outputs.files.singleFile.parentFile })
}

val generatedVersionFile = layout.buildDirectory.file("generated/version/version.properties")

val generatedVersion = provider {
	val f = generatedVersionFile.get().asFile
	if (f.exists()) {
		f.readText().removePrefix("version=").trim()
	} else {
		project.version.toString()
	}
}

tasks.jar {
	archiveBaseName = "autotweaker-core"
	archiveVersion = if ((System.getenv("GITHUB_REF") ?: "").startsWith("refs/tags/v")) {
		project.version.toString()
	} else {
		""
	}
}

tasks.withType<AbstractArchiveTask>().matching { it.name != "jar" }.configureEach {
	archiveVersion.set(generatedVersion)
}

// endregion
