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
	`maven-publish`
}

version = "0.1.0-alpha.4"

application {
	mainClass = "io.github.autotweaker.core.MainKt"
	applicationName = "autotweaker"
}

tasks.named<JavaExec>("run") {
	systemProperty("log.level", providers.systemProperty("log.level").orElse("DEBUG").get())
}

dependencies {
	implementation("io.ktor:ktor-client-core:3.4.3")
	implementation("io.ktor:ktor-client-java:3.4.3")
	implementation("io.ktor:ktor-client-cio:3.4.3")
	implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	
	
	testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
	testImplementation(kotlin("test"))
	testImplementation("io.mockk:mockk:1.14.9")
	testImplementation("io.ktor:ktor-client-mock:3.4.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("org.jetbrains.exposed:exposed-core:1.2.0")
	implementation("org.jetbrains.exposed:exposed-dao:1.2.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.2.0")
	
	implementation("com.h2database:h2:2.4.240")
	
	implementation("org.slf4j:slf4j-api:2.0.17")
	implementation("ch.qos.logback:logback-classic:1.5.32")
	
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("com.fasterxml.jackson.core:jackson-core:2.21.3")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
}

// region SettingItem.find() 编译前校验

tasks.register("validateFindCalls") {
	description = "校验 SettingItem.find() 的键引用和类型是否合法"
	notCompatibleWithConfigurationCache("访问文件系统和 Project 对象")
	
	doLast {
		val valueTypeToKotlinType = mapOf(
			"ValByte" to "Byte", "ValShort" to "Short", "ValInt" to "Int",
			"ValLong" to "Long", "ValFloat" to "Float", "ValDouble" to "Double",
			"ValBoolean" to "Boolean", "ValChar" to "Char", "ValString" to "String",
		)
		
		val configSerializerFile = rootProject.file(
			"config/src/main/kotlin/io/github/autotweaker/config/serializer/ConfigSerializer.kt"
		)
		if (!configSerializerFile.exists()) return@doLast
		
		// 从 ConfigSerializer.kt 提取 key→期望类型 映射
		val itemRegex = Regex("""SettingKey\("([^"]+)"\)\s*,\s*SettingItem\.Value\.(\w+)\(""")
		val promptRegex = Regex("""fromPrompt\("([^"]+)"\s*,\s*""")
		val registry = mutableMapOf<String, String>()
		val configSource = configSerializerFile.readText()
		for (m in itemRegex.findAll(configSource)) {
			val kotlinType = valueTypeToKotlinType[m.groupValues[2]] ?: continue
			registry[m.groupValues[1]] = kotlinType
		}
		for (m in promptRegex.findAll(configSource)) {
			registry[m.groupValues[1]] = "String"
		}
		
		val findRegex = Regex("""\.find\("([^"]+)"\)""")
		val typeCheckRegex = Regex(
			""":\s*(Byte|Short|Int|Long|Float|Double|Boolean|Char|String)\s*=\s*[^=]*?\.find\("([^"]+)"\)""",
			setOf(RegexOption.DOT_MATCHES_ALL)
		)
		
		fun lineOf(source: String, offset: Int): Int =
			source.substring(0, offset.coerceAtMost(source.length)).count { it == '\n' } + 1
		
		var errorCount = 0
		val sourceDir = file("src/main/kotlin")
		val baseDir = projectDir.toPath()
		
		sourceDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { ktFile ->
			val source = ktFile.readText()
			val rel = baseDir.relativize(ktFile.toPath())
			
			for (m in findRegex.findAll(source)) {
				val key = m.groupValues[1]
				if (key !in registry) {
					logger.error("{}:{}: 未知的配置键 \"{}\"", rel, lineOf(source, m.range.first), key)
					errorCount++
				}
			}
			
			for (m in typeCheckRegex.findAll(source)) {
				val declaredType = m.groupValues[1]
				val key = m.groupValues[2]
				val expectedType = registry[key] ?: continue
				if (declaredType != expectedType) {
					logger.error(
						"{}:{}: 类型不匹配 \"{}\" 期望 {} 但为 {}",
						rel,
						lineOf(source, m.range.first),
						key,
						expectedType,
						declaredType
					)
					errorCount++
				}
			}
		}
		
		if (errorCount > 0) {
			throw GradleException("SettingItem.find() validation failed with $errorCount error(s)")
		}
	}
}

tasks.named("compileKotlin") {
	dependsOn("validateFindCalls")
}

// endregion

tasks.test {
	jvmArgs("-Dnet.bytebuddy.experimental=true")
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
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
			ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true)
				.start().inputStream.bufferedReader().readText().trim()
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

// region Maven 发布到 GitHub Packages

publishing {
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/AutoTweaker/core")
			credentials {
				username = System.getenv("GITHUB_ACTOR") ?: ""
				password = System.getenv("GITHUB_TOKEN") ?: ""
			}
		}
	}
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			groupId = "io.github.autotweaker"
			artifactId = "core"
			version = project.version.toString()
		}
	}
}

// endregion

tasks.register<Exec>("compileAutotweakerCli") {
	description = "编译 C 编写的 autotweaker CLI 客户端"
	workingDir = file("${rootProject.projectDir}/cli")
	commandLine("make")
	inputs.dir(workingDir).withPropertyName("cliSourceDir").withPathSensitivity(PathSensitivity.RELATIVE)
	outputs.file("${workingDir}/build/autotweaker")
}

tasks.register<Exec>("buildDeb") {
	description = "构建 .deb 包"
	dependsOn("installDist", "compileAutotweakerCli")
	workingDir = rootProject.projectDir
	commandLine("bash", "scripts/build-deb.sh", project.version.toString())
}

tasks.register<Exec>("releaseTag") {
	description = "基于当前版本号打 tag 并推送"
	workingDir = rootProject.projectDir
	commandLine(
		"bash", "-c", """
		set -e
		if ! git diff --quiet || ! git diff --cached --quiet; then
			echo "错误: 工作区存在未提交的更改，请先提交或暂存所有更改后再执行 releaseTag" >&2
			exit 1
		fi
		git fetch origin main
		if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
			echo "错误: 本地 main 分支与 origin/main 不同步，请先同步后再执行 releaseTag" >&2
			exit 1
		fi
		git tag -a "v${project.version}" -m "AutoTweaker v${project.version}"
		git push origin "v${project.version}"
	""".trimIndent()
	)
}
