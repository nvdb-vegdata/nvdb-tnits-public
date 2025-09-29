plugins {
    id("tnits-conventions")
    id("org.springframework.boot") version "3.5.6"
}

publishing {
    publications {
        (publications["maven"] as MavenPublication).run {
            artifacts.clear()
            artifact(tasks["bootJar"])
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")
}
