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
	
	implementation("io.ktor:ktor-client-core:3.5.1")
	implementation("io.ktor:ktor-client-java:3.5.1")
	implementation("io.ktor:ktor-client-cio:3.5.1")
	implementation("io.ktor:ktor-client-content-negotiation:3.5.1")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	
	
	testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
	testImplementation(kotlin("test"))
	testImplementation("io.mockk:mockk:1.14.11")
	testImplementation("io.ktor:ktor-client-mock:3.5.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("org.jetbrains.exposed:exposed-core:1.3.1")
	implementation("org.jetbrains.exposed:exposed-dao:1.3.1")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.3.1")
	implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.3.1")
	
	implementation("com.h2database:h2:2.4.240")
	
	implementation("org.slf4j:slf4j-api:2.0.18")
	implementation("ch.qos.logback:logback-classic:1.5.37")
	implementation("com.dgkncgty:logback-journal:0.5.1")
	implementation("org.codehaus.janino:janino:3.1.12")
	implementation("net.logstash.logback:logstash-logback-encoder:9.0")
	
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("org.ow2.asm:asm:9.10.1")
	implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
	implementation("tools.jackson.core:jackson-core:3.2.1")
	implementation("tools.jackson.core:jackson-databind:3.2.1")
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
			"-Djava.security.egd=file:/dev/./urandom",
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

// region 版本资源

val generatedVersion = rootProject.ext["generatedVersion"] as String

val rootVersionFile = rootProject.layout.buildDirectory.file("generated/version/version.properties")

tasks.named<ProcessResources>("processResources") {
	from(rootVersionFile.map { it.asFile.parentFile })
}

tasks.jar {
	archiveBaseName = "autotweaker-core"
	if ((System.getenv("GITHUB_REF") ?: "").startsWith("refs/tags/v")) {
		archiveVersion.set(provider { generatedVersion })
	} else {
		archiveVersion.set("")
	}
}

tasks.withType<AbstractArchiveTask>().matching { it.name != "jar" }.configureEach {
	archiveVersion.set(provider { generatedVersion })
}

// endregion
