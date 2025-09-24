plugins {
    kotlin("jvm")
    id("org.springframework.boot") version "3.5.6"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter:3.5.6")
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")
    testImplementation(kotlin("test"))
}
