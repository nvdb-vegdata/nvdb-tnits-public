import gradle.kotlin.dsl.accessors._734a7a566e0c761836055f103d9b4672.publishing

plugins {
    id("tnits-conventions")
    id("org.springframework.boot") version "3.5.6"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks["bootJar"])
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")
}
