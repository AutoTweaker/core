plugins {
	kotlin("jvm")
	kotlin("kapt")
	id("org.jetbrains.kotlin.plugin.serialization")
	application
}

application {
	mainClass = "io.github.autotweaker.core.MainKt"
}

dependencies {
	implementation("io.ktor:ktor-client-core:3.4.3")
	implementation("io.ktor:ktor-client-java:3.4.3")
	implementation("io.ktor:ktor-client-cio:3.4.3")
	implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
	
	
	testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
	testImplementation(kotlin("test"))
	testImplementation("io.mockk:mockk:1.14.9")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	kapt("com.google.auto.service:auto-service:1.1.1")
	
	implementation("org.jetbrains.exposed:exposed-core:1.2.0")
	implementation("org.jetbrains.exposed:exposed-dao:1.2.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.2.0")
	
	implementation("com.h2database:h2:2.4.240")
	
	implementation("org.slf4j:slf4j-api:2.0.17")
	implementation("ch.qos.logback:logback-classic:1.5.32")
	
	implementation("com.github.docker-java:docker-java-core:3.7.1")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("com.fasterxml.jackson.core:jackson-core:2.21.3")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
}

tasks.test {
	jvmArgs("-Dnet.bytebuddy.experimental=true")
}
