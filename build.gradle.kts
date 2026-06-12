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
	kotlin("jvm") version "2.4.0" apply false
	id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
}

allprojects {
	repositories {
		mavenCentral()
	}
	group = "io.github.autotweaker"
	version = "0.1.0-alpha.32"
}

// region 生成版本资源文件

abstract class GitHashProvider : ValueSource<String, ValueSourceParameters.None> {
	override fun obtain(): String {
		return runCatching {
			val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start()
			val output = process.inputStream.bufferedReader().readText().trim()
			if (process.waitFor() != 0) throw RuntimeException("git exited non-zero")
			output
		}.getOrDefault("unknown")
	}
}

abstract class TimestampProvider : ValueSource<Long, ValueSourceParameters.None> {
	override fun obtain(): Long = System.currentTimeMillis() / 1000
}

val gitHash = providers.of(GitHashProvider::class) {}.get()

val timestamp = providers.of(TimestampProvider::class) {}.get()

val githubRef = providers.environmentVariable("GITHUB_REF").getOrElse("")

fun resolveVersion(baseVersion: String): String {
	return if (githubRef.startsWith("refs/tags/v")) {
		"${githubRef.removePrefix("refs/tags/v")}+$gitHash"
	} else {
		val stripped = baseVersion.replace(Regex("-[a-zA-Z].*"), "")
		"$stripped-dev+$timestamp.$gitHash"
	}
}

val generatedVersionFile = layout.buildDirectory.file("generated/version/version.properties")

// 配置阶段生成版本号
val generatedVersion = resolveVersion(project.version.toString())
generatedVersionFile.get().asFile.apply {
	parentFile.mkdirs()
	writeText("version=$generatedVersion")
}

ext["generatedVersion"] = generatedVersion

val generateVersionProperties by tasks.registering {
	description = "生成 version.properties（含 git hash 和构建时间戳）"
	outputs.file(generatedVersionFile)
}

subprojects {
	tasks.withType<ProcessResources>().configureEach {
		dependsOn(":generateVersionProperties")
	}
}

// endregion

tasks.register<Exec>("compileAutotweakerCli") {
	description = "编译 C 编写的 autotweaker CLI 客户端"
	workingDir = file("$projectDir/cli-client")
	commandLine("make")
	inputs.dir(workingDir).withPropertyName("cliSourceDir").withPathSensitivity(PathSensitivity.RELATIVE)
	outputs.file("${workingDir}/build/autotweaker")
}

tasks.register<Exec>("buildDeb") {
	description = "构建 .deb 包"
	dependsOn(":core:installDist", ":cli-adapter:jar", "compileAutotweakerCli", ":generateVersionProperties")
	workingDir = projectDir
	val cliAdapterJar = project(":cli-adapter").tasks.named("jar").get().outputs.files.singleFile.absolutePath
	val versionFile = generatedVersionFile
	doFirst {
		val version = versionFile.get().asFile.readText().removePrefix("version=").trim()
		commandLine("bash", "scripts/build-deb.sh", version, cliAdapterJar)
	}
}

tasks.register<Exec>("releaseTag") {
	description = "基于当前版本号打 tag 并推送"
	dependsOn(subprojects.map { "${it.path}:build" })
	workingDir = projectDir
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

tasks.register<Exec>("testInDocker") {
	dependsOn(subprojects.map { "${it.path}:compileKotlin" })
	group = "verification"
	description = "在 Docker 容器中运行单元测试"
	workingDir = projectDir
	commandLine(
		listOf("bash", "scripts/docker-test.sh") + (project.findProperty("testArgs") as String? ?: "").split(" ")
			.filter { it.isNotEmpty() })
	outputs.upToDateWhen { false }
}
