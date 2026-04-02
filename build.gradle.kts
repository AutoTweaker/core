plugins {
    kotlin("jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = "io.github.whiteelephant.autotweaker"
    version = "1.0.0"
}
