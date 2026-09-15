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
	id("org.jetbrains.dokka")
	`maven-publish`
	signing
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

java {
	withSourcesJar()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
	description = "将 Dokka 生成的文档打包为 javadoc jar"
	from(tasks.named("dokkaGeneratePublicationHtml"))
	archiveClassifier.set("javadoc")
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
		withType<MavenPublication>().configureEach {
			artifact(javadocJar)
			pom {
				name.set("AutoTweaker Tool Generator")
				description.set("Code generator for AutoTweaker tool declarations")
				url.set("https://github.com/AutoTweaker/core")
				licenses {
					license {
						name.set("GNU General Public License v3.0 or later")
						url.set("https://www.gnu.org/licenses/gpl-3.0.html")
					}
				}
				developers {
					developer {
						id.set("WhiteElephant-abc")
						name.set("WhiteElephant-abc")
						url.set("https://github.com/WhiteElephant-abc")
					}
				}
				scm {
					connection.set("scm:git:git://github.com/AutoTweaker/core.git")
					developerConnection.set("scm:git:ssh://git@github.com/AutoTweaker/core.git")
					url.set("https://github.com/AutoTweaker/core")
				}
			}
		}
	}
}

signing {
	val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
	if (signingKey != null) {
		useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingInMemoryKeyPassword").getOrElse(""))
		publishing.publications.configureEach { sign(this) }
	}
}
