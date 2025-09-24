import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("maven-publish")
}

group = "no.vegvesen.nvdb.tnits"
version = properties["version"]?.toString() ?: "1.0.0-SNAPSHOT"

ktlint {
    version.set("1.7.1")
}

repositories {
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
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

// Configure common settings for all subprojects
subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")

    repositories {
        maven { url = uri("https://repo.osgeo.org/repository/release/") }
        mavenCentral()
    }

    // Apply a specific Java toolchain to ease working on different environments
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Configure ktlint for all subprojects
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.1")
    }
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
