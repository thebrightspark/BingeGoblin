import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	kotlin("jvm") version libs.versions.kotlin
	alias(libs.plugins.kordex)
	alias(libs.plugins.kordex.i18n)
	`maven-publish`
}

group = "brightspark"
version = "1.1.0"

kotlin {
	jvmToolchain(21)
}

kordEx {
	bot {
		mainClass = "brightspark.MainKt"
		voice = false
		dataCollection(DataCollection.Minimal)
	}
}

i18n {
	bundle("bingegoblin.strings", "i18n")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.twitch4j)
	implementation(libs.ktor.client.core)
	implementation(libs.ktor.client.cio)
	implementation(libs.ktor.client.contentnegotiation)
	implementation(libs.ktor.serialization.jackson)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.dataformat.properties)
	implementation(libs.jackson.module.kotlin)
	implementation(libs.logback)
}

publishing {
	publications {
		create<MavenPublication>("bingegoblin") {
			from(components["java"])
		}
	}
//	repositories {
//		maven {
//			name = "GitLab"
//			url = uri("https://gitlab.com/api/v4/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
//			credentials(HttpHeaderCredentials::class) {
//				name = "Job-Token"
//				value = System.getenv("CI_JOB_TOKEN")
//			}
//			authentication {
//				create<HttpHeaderAuthentication>("header")
//			}
//		}
//	}
}
