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
	`maven-publish`
}

dependencies {
	implementation("com.squareup:kotlinpoet:2.1.0")
	runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.4.0")
	runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.4.0")
}

kotlin {
	jvmToolchain(25)
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
	archiveClassifier.set("sources")
	from(sourceSets.main.get().allSource)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			artifact(sourcesJar)
		}
	}
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
