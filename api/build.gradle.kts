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
	id("org.jetbrains.kotlin.plugin.serialization")
	`maven-publish`
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

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
			artifactId = "api"
			version = rootProject.ext["generatedVersion"] as String
		}
	}
}

// endregion
