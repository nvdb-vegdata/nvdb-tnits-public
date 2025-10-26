plugins {
    id("tnits-conventions")
}

dependencies {

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")

    testFixturesApi(platform("org.testcontainers:testcontainers-bom:2.0.1"))

    // Testcontainers for integration testing
    testFixturesApi("org.testcontainers:testcontainers-minio")
    testFixturesApi("io.minio:minio:8.6.0")
}
