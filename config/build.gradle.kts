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
	application
}

application {
	mainClass = "io.github.autotweaker.config.serializer.ConfigSerializerKt"
}

dependencies {
	implementation(project(":core"))
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.register<JavaExec>("serializeConfig") {
	description = "序列化默认配置到 JSON 文件"
	dependsOn("classes")
	
	classpath = sourceSets.main.get().runtimeClasspath
	mainClass.set("io.github.autotweaker.config.serializer.ConfigSerializerKt")
	args("${rootProject.rootDir}/.temp/default_config/AppConfig.json")
}
