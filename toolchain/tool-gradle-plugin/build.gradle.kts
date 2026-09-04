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
	`java-gradle-plugin`
	`maven-publish`
}

kotlin {
	jvmToolchain(25)
}

dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

gradlePlugin {
	plugins {
		create("toolgen") {
			id = "io.github.autotweaker.toolgen"
			implementationClass = "io.github.autotweaker.toolgradle.ToolgenPlugin"
		}
	}
}

val versionResDir = layout.buildDirectory.dir("generated/toolgradle/version")
val pluginVersion = project.version.toString()

val versionFile = versionResDir.get().file("io/github/autotweaker/toolgradle/toolgen.properties")
versionFile.asFile.apply {
	parentFile.mkdirs()
	writeText("toolgen=$pluginVersion")
}

sourceSets["main"].resources.srcDir(versionResDir)

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
