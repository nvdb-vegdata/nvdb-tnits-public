plugins {
    id("tnits-conventions")
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version "2.2.20"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.bootJar)
        }
    }
}

dependencies {
    implementation(project(":tnits-common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
}
