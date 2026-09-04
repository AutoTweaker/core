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
	kotlin("jvm") version "2.4.10" apply false
}

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

val toolchainProps = java.util.Properties().apply {
	file("../gradle.properties").inputStream().use { load(it) }
}

val baseVersion: String = toolchainProps.getProperty("version")
val gitHash = providers.of(GitHashProvider::class) {}.get()
val githubRef = providers.environmentVariable("GITHUB_REF").getOrElse("")
val resolvedVersion = if (githubRef.startsWith("refs/tags/v")) {
	"${githubRef.removePrefix("refs/tags/v")}+$gitHash"
} else baseVersion

allprojects {
	group = toolchainProps.getProperty("group")
	version = resolvedVersion
	repositories {
		mavenCentral()
	}
}
