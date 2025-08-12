import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("io.ktor.plugin") version "3.2.3"
    id("org.openapi.generator") version "7.14.0"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
    id("com.github.ben-manes.versions") version "0.52.0"
    application
}

group = "no.vegvesen.nvdb"
version = "1.0.0"

repositories {
    mavenCentral()
}

val jaxb by configurations.creating

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.github.smiley4:ktor-openapi:5.2.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.2.0")
    implementation("io.github.smiley4:ktor-redoc:5.2.0")

    // Ktor Client (for NVDB API calls)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-logging")

    // JSON processing for generated Java client (Latest Jackson versions)
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")

    // OpenAPI Generator dependencies
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

    // Jakarta EE annotations (modern standard)
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:1.0.0-beta-5")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-beta-5")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.0.0-beta-5")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.zaxxer:HikariCP:6.0.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // OpenLR
    implementation("org.locationtech.jts:jts-core:1.20.0")

    // JAXB for XML schema binding
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
    jaxb("org.glassfish.jaxb:jaxb-xjc:4.0.5")
    jaxb("org.glassfish.jaxb:jaxb-runtime:4.0.5")

    // Pre-built GML 3.2.1 JAXB bindings
    implementation("org.jvnet.ogc:gml-v_3_2_1:2.6.1")
    jaxb("org.jvnet.ogc:gml-v_3_2_1:2.6.1")

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
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

openApiGenerate {
    generatorName.set("java")
    inputSpec.set("$projectDir/nvdb-api.json")
    outputDir.set("${layout.buildDirectory.get()}/generated")
    packageName.set("no.vegvesen.nvdb.client")
    configOptions.set(
        mapOf(
            "library" to "native",
            "useJakartaEe" to "true",
            "hideGenerationTimestamp" to "true",
        ),
    )
}

// Custom JAXB task to generate TN-ITS classes from XSD
tasks.register<JavaExec>("generateTnItsClasses") {
    group = "build"
    description = "Generate TN-ITS Java classes from XSD schemas"

    classpath = jaxb
    mainClass.set("com.sun.tools.xjc.XJCFacade")

    val outputDir = "${layout.buildDirectory.get()}/generated-sources/jaxb"
    val packageName = "no.vegvesen.nvdb.tnits.model"

    doFirst {
        file(outputDir).mkdirs()
    }

    args =
        listOf(
            "-d",
            outputDir,
            "-extension",
            "-nv",
            "-p",
            packageName,
            "schemas/tnits/openlr.xsd",
        )

    inputs.dir("src/main/xsd")
    inputs.dir("schemas")
    outputs.dir(outputDir)
}

sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
        java {
            srcDir("${layout.buildDirectory.get()}/generated/src/main/java")
            srcDir("${layout.buildDirectory.get()}/generated-sources/jaxb")
        }
    }
}

tasks.compileKotlin {
    dependsOn(tasks.openApiGenerate)
    dependsOn("generateTnItsClasses")
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
    dependsOn("generateTnItsClasses")
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn(tasks.openApiGenerate)
    dependsOn("generateTnItsClasses")
}

// Git hooks setup
tasks.register("installGitHooks") {
    description = "Install git hooks for code formatting"
    group = "build setup"
    notCompatibleWithConfigurationCache("Task manipulates files outside of project directory")

    doLast {
        val hooksDir = File(rootDir, ".git/hooks")
        val preCommitHook = File(hooksDir, "pre-commit")
        val preCommitTemplate = File(rootDir, "git-hooks/pre-commit")

        if (!preCommitTemplate.exists()) {
            throw GradleException("Pre-commit hook template not found at: ${preCommitTemplate.absolutePath}")
        }

        preCommitHook.writeText(preCommitTemplate.readText())
        preCommitHook.setExecutable(true)

        println("✅ Git pre-commit hook installed successfully")
        println("The hook will automatically run ktlint formatting on commits")
    }
}
