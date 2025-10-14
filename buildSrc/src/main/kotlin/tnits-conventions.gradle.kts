import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    id("org.jlleitschuh.gradle.ktlint")
    id("com.github.ben-manes.versions")
    id("maven-publish")
}

group = "no.vegvesen.nvdb.tnits"
version = properties["version"]?.toString() ?: "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

ktlint {
    version = "1.7.1"
    filter {
        exclude("**/generated/**")
    }
}

dependencies {
    // Minio S3
    implementation("io.minio:minio:8.6.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

publishing {
    repositories {
        maven {
            credentials {
                username = properties["artrepoUser"].toString()
                password = properties["artrepoPass"].toString()
            }
            val releaseRepo = "https://artrepo.vegvesen.no/artifactory/libs-release-local"
            val snapshotRepo = "https://artrepo.vegvesen.no/artifactory/libs-snapshot-local"
            url = uri(if ((version.toString()).endsWith("SNAPSHOT")) snapshotRepo else releaseRepo)
        }
    }
}

repositories {
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
    mavenCentral()
}
