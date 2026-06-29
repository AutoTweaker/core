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

import java.net.URI

plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
	id("org.jetbrains.dokka")
	`maven-publish`
}

dokka {
	dokkaPublications.html {
		moduleName.set("AutoTweaker API")
		outputDirectory.set(layout.buildDirectory.dir("dokka/api"))
	}
	dokkaSourceSets.configureEach {
		sourceLink {
			localDirectory.set(file("src"))
			remoteUrl.set(URI("https://github.com/AutoTweaker/core/blob/main/api/src"))
			remoteLineSuffix.set("#L")
		}
	}
}

kotlin {
	jvm()
	linuxX64()
	
	sourceSets {
		commonMain.dependencies {
			implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
			implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
		}
		jvmMain.dependencies {
			implementation("org.slf4j:slf4j-api:2.0.18")
		}
	}
}

// region Maven publish to GitHub Packages

group = "io.github.autotweaker"
version = rootProject.ext["generatedVersion"] as String

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
}

// endregion
