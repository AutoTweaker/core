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

@file:Suppress("UnstableApiUsage")

import io.github.autotweaker.plugin.versioning.VersionMode

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("io.github.autotweaker.plugin.versioning") version "3.0.0"
	id("com.gradleup.nmcp.settings") version "1.6.2"
}

nmcpSettings {
	centralPortal {
		username = providers.gradleProperty("centralPortalUsername").getOrElse("")
		password = providers.gradleProperty("centralPortalPassword").getOrElse("")
		publishingType = "USER_MANAGED"
	}
}

rootProject.name = "AutoTweaker"

include("core")
project(":core").name = "autotweaker-core"
include("api")
project(":api").name = "autotweaker-api"
include("cli-adapter")
include("cli-debugger")
include("cli-client")
include("cli-protocol")
include("tool-decl")
include("tool-gen")
project(":tool-gen").name = "autotweaker-tool-gen"
include("plugin-sdk")
project(":plugin-sdk").name = "autotweaker-plugin-sdk"

versioning {
	mode.set(
		if (System.getenv("AUTOTWEAKER_RELEASE") == "1") VersionMode.RELEASE else VersionMode.DEV
	)
}
