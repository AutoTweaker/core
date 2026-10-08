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
}

val toolgenMeta = configurations.register("toolgenMeta") {
	isCanBeConsumed = false
	isCanBeResolved = true
	isTransitive = false
}
dependencies.add(toolgenMeta.name, dependencies.project(path = ":tool-decl", configuration = "generatedCoreMeta"))

val syncGeneratedMeta = tasks.register<Sync>("syncGeneratedMeta") {
	description = "同步 tool-decl 生成的 ToolMeta 源码到 core 模块"
	from(toolgenMeta)
	into(layout.buildDirectory.dir("generated/args/io/github/autotweaker/core"))
}

kotlin {
	sourceSets["main"].kotlin.srcDir(
		project.files(layout.buildDirectory.dir("generated/args")).builtBy(syncGeneratedMeta)
	)
}

tasks.configureEach {
	if (name.startsWith("compileKotlin") || name.startsWith("kaptGenerateStubs")) {
		dependsOn("syncGeneratedMeta")
	}
}

application {
	mainClass = "io.github.autotweaker.core.MainKt"
	applicationName = "autotweaker"
	applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
	systemProperty("log.level", providers.systemProperty("log.level").orElse("DEBUG").get())
}

dependencies {
	implementation(project(":autotweaker-api"))
	
	implementation("io.ktor:ktor-client-core:3.6.0")
	implementation("io.ktor:ktor-client-java:3.6.0")
	implementation("io.ktor:ktor-client-cio:3.6.0")
	implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

	implementation("com.twelvemonkeys.imageio:imageio-core:3.15.3")
	implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.15.3")
	implementation("com.twelvemonkeys.imageio:imageio-tiff:3.15.3")
	implementation("com.twelvemonkeys.imageio:imageio-bmp:3.15.3")
	implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")
	implementation("com.twelvemonkeys.imageio:imageio-metadata:3.15.3")
	

	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("org.jetbrains.exposed:exposed-core:1.5.0")
	implementation("org.jetbrains.exposed:exposed-dao:1.5.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
	implementation("org.jetbrains.exposed:exposed-json:1.5.0")
	implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.5.0")
	
	implementation("com.h2database:h2:2.5.252")
	
	implementation("org.apache.lucene:lucene-core:10.5.1")
	implementation("org.apache.lucene:lucene-analysis-common:10.5.1")
	
	implementation("org.slf4j:slf4j-api:2.0.20")
	implementation("ch.qos.logback:logback-classic:1.6.5")
	implementation("com.dgkncgty:logback-journal:0.5.1")
	implementation("org.codehaus.janino:janino:3.1.12")
	implementation("net.logstash.logback:logstash-logback-encoder:9.0")
	
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.apache.httpcomponents.client5:httpclient5:5.6.4")
	implementation("org.apache.httpcomponents.core5:httpcore5:5.4.3")
	implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
	implementation("org.bouncycastle:bcprov-jdk18on:1.86")
	implementation("org.ow2.asm:asm:9.10.1")
	implementation("com.fasterxml.jackson.core:jackson-core:2.22.3")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
	implementation("tools.jackson.core:jackson-core:3.2.3")
	implementation("tools.jackson.core:jackson-databind:3.2.3")
	
	implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")
	implementation("com.google.guava:guava:33.7.1-jre")
	implementation("com.ibm.icu:icu4j:78.3")
	implementation("io.insert-koin:koin-core:4.2.2")
	implementation("io.github.java-diff-utils:java-diff-utils:4.17")
}
afterEvaluate {
	extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KaptExtensionConfig>().javacOptions {
		option("-nowarn")
	}
}

val exportRuntime = tasks.register<Sync>("exportRuntime") {
	description = "导出生产运行时产物集合（core jar + 全部依赖 jar），供 AutoTweaker/test 仓库引用"
	from(tasks.named("jar"))
	from(configurations.named("runtimeClasspath")) { include("*.jar") }
	into(layout.buildDirectory.dir("runtime"))
}
