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
    dependsOn("classes")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.autotweaker.config.serializer.ConfigSerializerKt")
    args("${rootProject.rootDir}/.temp/default_config/AppConfig.json")
}
