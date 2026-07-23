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

val kotlincBin by lazy {
	project.findProperty("kotlinc.bin") as String?
		?: if (file("/.flatpak-info").exists()) "flatpak-spawn --host kotlinc" else "kotlinc"
}

val scripts = fileTree("src") { include("*.main.kts") }
val outputDir = layout.buildDirectory.dir("generated/args")
val rootPath: java.nio.file.Path = rootDir.toPath()

val proxyArgs by lazy {
	val proxyEnv = listOf(
		System.getenv("HTTPS_PROXY"),
		System.getenv("https_proxy"),
		System.getenv("HTTP_PROXY"),
		System.getenv("http_proxy"),
	).firstOrNull { !it.isNullOrBlank() } ?: return@lazy listOf()
	runCatching {
		val uri = java.net.URI(proxyEnv)
		listOf(
			"-J-Dhttps.proxyHost=${uri.host}",
			"-J-Dhttps.proxyPort=${uri.port}",
			"-J-Dhttp.proxyHost=${uri.host}",
			"-J-Dhttp.proxyPort=${uri.port}",
		)
	}.getOrDefault(emptyList())
}

tasks.register<Exec>("generateArgs") {
	group = "generate"
	description = "from .main.kts declarations generate ToolArgs"
	dependsOn(":tool-gen:publishToMavenLocal")
	inputs.files(scripts)
	inputs.files(fileTree("$rootDir/tool-gen/src/main/kotlin") { include("**/*.kt") })
	outputs.dir(outputDir)
	executable = "bash"
	workingDir = rootDir
	val relOut = rootPath.relativize(outputDir.get().asFile.toPath()).toString()
	val proxyStr = proxyArgs.joinToString(" ")
	val cmd = "rm -rf '$relOut' && " + scripts.files.sortedBy { it.name }.joinToString(" && ") { f ->
		val relScript = rootPath.relativize(f.toPath()).toString()
		"$kotlincBin $proxyStr -J-Dtoolgen.outputDir='$relOut' -script '$relScript'"
	}
	args("-c", cmd)
}
