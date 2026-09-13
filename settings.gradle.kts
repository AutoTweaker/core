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

import io.github.autotweaker.gradle.plugin.versioning.VersionMode

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/AutoTweaker/gradle-versioning")
			credentials {
				username = providers.gradleProperty("gpr.user").getOrElse("")
				password = providers.gradleProperty("gpr.key").getOrElse("")
			}
		}
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("io.github.autotweaker.versioning") version "1.0.0"
}

rootProject.name = "AutoTweaker"

include("core")
include("api")
include("cli-adapter")
include("cli-debugger")
include("cli-client")
include("cli-protocol")
include("tool-decl")
include("tool-gen")
include("plugin-sdk")

versioning {
	versionMode.set(
		if (System.getenv("AUTOTWEAKER_RELEASE") == "1") VersionMode.RELEASE else VersionMode.DEV
	)
}
