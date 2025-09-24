plugins {
    kotlin("jvm")
    id("org.springframework.boot") version "3.5.6"
}

group = "no.vegvesen.nvdb.tnits"
version = properties["version"]?.toString() ?: "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter:3.5.6")
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
