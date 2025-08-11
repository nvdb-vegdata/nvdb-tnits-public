import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("io.ktor.plugin") version "3.2.2"
    id("org.openapi.generator") version "7.9.0"
    application
}

group = "no.vegvesen.nvdb"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    
    // Ktor Client (for NVDB API calls)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-logging")
    
    // OkHttp for generated client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.zaxxer:HikariCP:6.0.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
    
    // OpenLR
    implementation("org.locationtech.jts:jts-core:1.20.0")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}

application {
    mainClass.set("no.vegvesen.nvdb.ApplicationKt")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/nvdb-api.json")
    outputDir.set("${layout.buildDirectory.get()}/generated")
    packageName.set("no.vegvesen.nvdb.client")
    configOptions.set(mapOf(
        "dateLibrary" to "kotlinx-datetime",
        "enumPropertyNaming" to "UPPERCASE",
        "serializationLibrary" to "kotlinx_serialization"
    ))
}

// Temporarily disabled until API client issues are resolved
// sourceSets {
//     main {
//         kotlin {
//             srcDir("${layout.buildDirectory.get()}/generated/src/main/kotlin")
//         }
//     }
// }

// Temporarily disabled
// tasks.compileKotlin {
//     dependsOn(tasks.openApiGenerate)
// }