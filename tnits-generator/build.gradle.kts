plugins {
    kotlin("jvm")
    application
}

group = "no.vegvesen.vt.nvdb.tnits"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "no.vegvesen.vt.nvdb.tnits.generator.MainKt"
}

tasks {
    jar {
        manifest {
            attributes(
                "Main-Class" to "no.vegvesen.vt.nvdb.tnits.generator.MainKt"
            )
        }
    }
}
