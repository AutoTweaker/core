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
	kotlin("jvm") version "2.4.10"
	`maven-publish`
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.squareup:kotlinpoet:2.3.0")
	implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.4.10")
	implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.4.10")
	implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.4.10")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin {
	jvmToolchain(25)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/AutoTweaker/core")
			credentials {
				username = providers.gradleProperty("gpr.user").getOrElse("")
				password = providers.gradleProperty("gpr.key").getOrElse("")
			}
		}
	}
}
