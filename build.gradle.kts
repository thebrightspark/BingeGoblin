import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	kotlin("jvm") version "2.1.0"
	id("dev.kordex.gradle.kordex") version "1.7.0"
}

group = "brightspark"
version = "1.0.0"

kotlin {
	jvmToolchain(21)
}

kordEx {
	bot {
		mainClass = "brightspark.MainKt"
		voice = false
		dataCollection(DataCollection.Minimal)
	}
	i18n {
		classPackage = "i18n"
		translationBundle = "bingegoblin.strings"
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.github.twitch4j:twitch4j-helix:1.24.0")

	val ktorVersion = "3.1.3"
	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-cio:$ktorVersion")
	implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
	implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

	val jacksonVersion = "2.19.0"
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:$jacksonVersion")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

//	implementation("org.slf4j:slf4j-simple:2.0.17")
	implementation("ch.qos.logback:logback-classic:1.5.18")
}
