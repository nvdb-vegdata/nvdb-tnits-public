plugins {
    id("tnits-conventions")
    id("org.springframework.boot") version "3.5.8"
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

    testImplementation(testFixtures(project(":tnits-common")))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Jackson YAML
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-extensions-spring")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("no.nav.security:mock-oauth2-server:3.0.0")
}
