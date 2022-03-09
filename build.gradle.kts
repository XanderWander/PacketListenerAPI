import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20-M1"
    `maven-publish`
}

val baseAuthor: String by project
val baseName: String by project
val baseVersion: String by project
val author = baseAuthor.toLowerCase()
val name = baseName.toLowerCase()

group = "$author.$baseName"
version = baseVersion

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.74.Final")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}