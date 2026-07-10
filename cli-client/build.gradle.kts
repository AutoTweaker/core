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
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}

kotlin {
	linuxX64 {
		binaries {
			executable {
				baseName = "autotweaker"
				entryPoint = "io.github.autotweaker.adapter.cli.client.main"
			}
		}
	}
	
	sourceSets {
		commonMain.dependencies {
			implementation(project(":api"))
			implementation(project(":cli-protocol"))
			implementation("io.ktor:ktor-network:3.5.1")
			implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
			implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
		}
	}
}
