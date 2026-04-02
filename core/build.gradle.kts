plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

application {
    mainClass = "io.github.whiteelephant.autotweaker.core.MainKt"
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin/io/github/whiteelephant/autotweaker/core")
        }
    }
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.4.2")
    implementation("io.ktor:ktor-client-java:3.4.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("ch.qos.logback:logback-classic:1.5.32")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
