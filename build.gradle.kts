import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20-M1"
}

val baseAuthor: String by project
val baseName: String by project
val baseVersion: String by project
val author = baseAuthor.toLowerCase()
val name = baseName.toLowerCase()

group = "nl.$author.$name"
version = baseVersion

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.24.Final")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/xanderwander/clientstandapi")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}