# Testing Guide

This document describes the testing framework, conventions, and best practices for the nvdb-tnits project.

## Testing Framework

The project uses **Kotest** as its primary testing framework.

**Why Kotest?**

- Expressive test names with natural language
- Multiple testing styles (ShouldSpec, FunSpec, etc.)
- Powerful matchers and assertions
- Property-based testing support
- Better Kotlin integration than JUnit

**Documentation:** [kotest.io](https://kotest.io)

**Version:** 6.0.0

## Test Structure

### Test Location

Tests are located in the `src/test/kotlin` directory, mirroring the source structure:

```
src/
├── main/kotlin/no/vegvesen/nvdb/tnits/
│   ├── infrastructure/rocksdb/
│   │   └── VeglenkerRocksDbStore.kt
│   └── core/presentation/
│       └── XmlStreamDsl.kt
└── test/kotlin/no/vegvesen/nvdb/tnits/
    ├── storage/
    │   └── VeglenkerRocksDbStoreTest.kt
    └── xml/
        └── XmlStreamDslTest.kt
```

### Test Resources

Test resources are in `src/test/resources`:

```
src/test/resources/
├── vegobjekt-105-85283590.json        # Single speed limit
├── vegobjekt-105-85283803.json
├── veglenkesekvens-41423.json         # Single veglenkesekvens
├── veglenkesekvenser-41423-42424.json # Multiple veglenkesekvenser
└── application-test.conf              # Test configuration
```

**Naming conventions:**

- Speed limits: `vegobjekt-105-{id}.json`
- Other vegobjekter: `vegobjekt-{typeId}-{id}.json`
- Single veglenkesekvens: `veglenkesekvens-{id}.json`
- Multiple veglenkesekvenser: `veglenkesekvenser-{id1}-{idX}.json`

**Saving test data:**

```bash
# Fetch and save a vegobjekt
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/85283590?inkluder=alle" | \
    jq '.' > src/test/resources/vegobjekt-105-85283590.json

# Fetch and save veglenkesekvenser
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=41423,42424" | \
    jq '.' > src/test/resources/veglenkesekvenser-41423-42424.json
```

See: `CLAUDE.md` for test resource guidelines

## Test Styles

### ShouldSpec

The primary testing style used in this project:

```kotlin
class VeglenkerRocksDbStoreTest : ShouldSpec({
    should("store and retrieve veglenker") {
        // Arrange
        val store = VeglenkerRocksDbStore(rocksDbContext)
        val veglenker = listOf(
            Veglenke(id = 1, lengde = 100.0, geometri = lineString1),
            Veglenke(id = 2, lengde = 200.0, geometri = lineString2)
        )

        // Act
        store.upsert(veglenkesekvensId = 12345, veglenker)
        val result = store.get(veglenkesekvensId = 12345)

        // Assert
        result shouldNotBe null
        result shouldHaveSize 2
        result?.first()?.lengde shouldBe 100.0
    }

    should("return null for non-existent veglenkesekvens") {
        val store = VeglenkerRocksDbStore(rocksDbContext)
        val result = store.get(veglenkesekvensId = 99999)

        result shouldBe null
    }
})
```

**Characteristics:**

- Test names are strings (natural language)
- Tests organized with `should("description")`
- Supports nested contexts
- Clear test structure

### Test Organization

For longer tests, use comments to separate AAA (Arrange-Act-Assert) sections:

```kotlin
should("calculate intersecting geometry correctly") {
    // Arrange
    val veglenkeGeometry = parseWkt("LINESTRING(0 0, 10 0)", SRID.UTM33)
    val veglenkeUtstrekning = StedfestingUtstrekning(
        veglenkesekvensId = 1,
        startposisjon = 0.0,
        sluttposisjon = 1.0
    )
    val stedfestingUtstrekning = StedfestingUtstrekning(
        veglenkesekvensId = 1,
        startposisjon = 0.25,
        sluttposisjon = 0.75
    )

    // Act
    val result = calculateIntersectingGeometry(
        veglenkeGeometry,
        veglenkeUtstrekning,
        stedfestingUtstrekning
    )

    // Assert
    result shouldNotBe null
    result?.length shouldBe (5.0 plusOrMinus 0.01)
}
```

## Running Tests

### Run All Tests

```bash
./gradlew test
```

### Run Tests in Specific Module

```bash
./gradlew tnits-generator:test
```

### Run Specific Test Class

```bash
./gradlew test --tests='XmlStreamDslTest'
```

**Important:** Use exact class name, not wildcards:

```bash
# Good
./gradlew test --tests='VeglenkerRocksDbStoreTest'

# Bad - doesn't work with Kotest
./gradlew test --tests='*RocksDbStoreTest'
```

### Run Specific Test Case

Use `kotest_filter_tests` environment variable with wildcards:

```bash
kotest_filter_tests="*should format with padded vegobjekttype*" \
    ./gradlew test --tests="SpeedLimitExporterTest"
```

**Pattern:**

```bash
kotest_filter_tests="*pattern*" ./gradlew test --tests="ExactClassName"
```

**Important rules from `CLAUDE.md`:**

- NEVER use asterisks (*) in `--tests` parameter
- ALWAYS use exact class names in `--tests`
- For wildcards, use `kotest_filter_tests` environment variable
- Use `--info --rerun` for detailed output and force re-run

### Run Tests with Detailed Output

```bash
./gradlew test --info
```

### Force Re-run Tests

```bash
./gradlew test --rerun
```

### Combined Example

```bash
kotest_filter_tests="*merge connected paths*" \
    ./gradlew test --info --rerun --tests="OpenLrServiceTest"
```

## Kotest Assertions

### Basic Matchers

```kotlin
// Equality
result shouldBe expected
result shouldNotBe unexpected

// Null checks
result shouldBe null
result shouldNotBe null

// Collections
list shouldHaveSize 5
list shouldContain item
list shouldContainAll listOf(item1, item2)
list.shouldBeEmpty()
list.shouldNotBeEmpty()

// Strings
str shouldStartWith "prefix"
str shouldEndWith "suffix"
str shouldContain "substring"
str shouldMatch "regex pattern"

// Numeric
value shouldBe (10.0 plusOrMinus 0.01)
value shouldBeGreaterThan 5
value shouldBeLessThan 10
value shouldBeInRange 5..10

// Booleans
condition.shouldBeTrue()
condition.shouldBeFalse()

// Exceptions
shouldThrow<IllegalArgumentException> {
    // code that should throw
}

val exception = shouldThrow<RocksDBException> {
    // code that should throw
}
exception.message shouldContain "expected text"
```

**Documentation:** [kotest.io/docs/assertions](https://kotest.io/docs/assertions/assertions.html)

### Custom Matchers

```kotlin
fun Geometry.shouldHaveSrid(expectedSrid: Int) {
    this.srid shouldBe expectedSrid
}

// Usage
geometry.shouldHaveSrid(SRID.WGS84)
```

## Test Utilities

### Temporary RocksDB

For tests requiring RocksDB:

```kotlin
class MyTest : ShouldSpec({
    should("test with RocksDB") {
        TempRocksDbConfig.withTempDb { rocksDbContext ->
            val store = VeglenkerRocksDbStore(rocksDbContext)

            // Test code here
            store.upsert(1, veglenker)
            val result = store.get(1)

            result shouldNotBe null
        }
    }
})
```

**Implementation:** `openlr/TempRocksDbConfig.kt`

**Features:**

- Creates temporary RocksDB instance
- Automatically cleans up after test
- Isolated from production database

### Test Helpers

**Location:** `TestHelpers.kt`

```kotlin
// Load test resource
fun loadTestResource(path: String): String {
    return javaClass.getResourceAsStream(path)
        ?.bufferedReader()
        ?.readText()
        ?: error("Resource not found: $path")
}

// Parse test vegobjekt
fun loadTestVegobjekt(typeId: Int, objektId: Long): Vegobjekt {
    val json = loadTestResource("/vegobjekt-$typeId-$objektId.json")
    return objectMapper.readValue(json)
}
```

## Integration Testing

### TestContainers

For tests requiring MinIO/S3:

```kotlin
class S3IntegrationTest : ShouldSpec({
    val minioContainer = MinioContainer("minio/minio:latest")
        .withUserName("testuser")
        .withPassword("testpassword")

    beforeSpec {
        minioContainer.start()
    }

    afterSpec {
        minioContainer.stop()
    }

    should("upload file to MinIO") {
        val minioClient = MinioClient.builder()
            .endpoint(minioContainer.s3URL)
            .credentials("testuser", "testpassword")
            .build()

        // Test S3 operations
        minioClient.makeBucket(MakeBucketArgs.builder().bucket("test").build())

        // Assertions
        minioClient.bucketExists(BucketExistsArgs.builder().bucket("test").build())
            .shouldBeTrue()
    }
})
```

**MinIO test helper:** `MinioTestHelper.kt`

### E2E Tests

End-to-end tests that exercise the full pipeline:

```kotlin
class TnitsExportE2ETest : ShouldSpec({
    should("export speed limits end-to-end") {
        TempRocksDbConfig.withTempDb { rocksDbContext ->
            MinioTestHelper.withMinioContainer { minioClient ->
                // 1. Setup test data
                val veglenkerStore = VeglenkerRocksDbStore(rocksDbContext)
                veglenkerStore.upsert(1, testVeglenker)

                val vegobjekterStore = VegobjekterRocksDbStore(rocksDbContext)
                vegobjekterStore.upsert(105, 1, testSpeedLimit)

                // 2. Run export
                val exporter = TnitsFeatureExporter(...)
                exporter.exportSnapshot(clock.now(), ExportedFeatureType.SpeedLimit)

                // 3. Verify output
                val exportedFiles = minioClient.listObjects(...)
                exportedFiles.shouldNotBeEmpty()

                val xmlContent = minioClient.getObject(...)
                xmlContent shouldContain "<tn-its:speedLimit"
            }
        }
    }
})
```

See: `TnitsExportE2ETest.kt`

## Mocking

The project uses **MockK** for mocking:

```kotlin
class MyServiceTest : ShouldSpec({
    should("call API with correct parameters") {
        // Create mock
        val mockApi = mockk<UberiketApi>()

        // Setup expectations
        coEvery {
            mockApi.fetchVegobjekt(typeId = 105, objektId = 123)
        } returns testVegobjekt

        // Use mock
        val service = MyService(mockApi)
        val result = service.processVegobjekt(105, 123)

        // Verify
        result shouldNotBe null
        coVerify(exactly = 1) {
            mockApi.fetchVegobjekt(typeId = 105, objektId = 123)
        }
    }
})
```

**Documentation:** [mockk.io](https://mockk.io)

## Test Data Management

### Loading Test Resources

```kotlin
val vegobjektJson = loadTestResource("/vegobjekt-105-85283590.json")
val vegobjekt = objectMapper.readValue<Vegobjekt>(vegobjektJson)
```

### Creating Test Data

```kotlin
val testVeglenke = Veglenke(
    id = 1,
    veglenkesekvensId = 12345,
    startposisjon = 0.0,
    sluttposisjon = 1.0,
    lengde = 100.0,
    geometri = parseWkt("LINESTRING(0 0, 100 0)", SRID.UTM33)
)

val testStedfesting = StedfestingUtstrekning(
    veglenkesekvensId = 12345,
    startposisjon = 0.25,
    sluttposisjon = 0.75,
    retning = Retning.MED
)
```

### Test Builders

For complex objects, use builder pattern:

```kotlin
fun buildTestVegobjekt(
    typeId: Int = 105,
    objektId: Long = 1,
    speedLimit: Int = 80,
    stedfesting: Stedfesting = defaultStedfesting
): Vegobjekt {
    return Vegobjekt(
        id = objektId,
        type = VegobjektType(id = typeId),
        egenskaper = listOf(
            Egenskap(
                id = EgenskapsTyper.FARTSGRENSE,
                verdi = speedLimit
            )
        ),
        stedfesting = stedfesting,
        metadata = Metadata(
            startdato = Instant.parse("2020-01-01T00:00:00Z"),
            versjon = 1
        )
    )
}
```

## Testing Best Practices

### Test Naming

Use descriptive test names that explain what's being tested:

```kotlin
// Good
should("return empty list when veglenkesekvens does not exist") { }
should("calculate correct offset for start position") { }
should("merge connected paths into single OpenLR reference") { }

// Bad
should("test get") { }
should("test calculation") { }
should("test merge") { }
```

### One Assertion Per Test?

For simple tests, one logical assertion is fine. For complex scenarios, multiple related assertions are acceptable:

```kotlin
should("deserialize vegobjekt correctly") {
    val result = deserialize(json)

    // Multiple related assertions about the same object
    result.id shouldBe 85283590
    result.typeId shouldBe 105
    result.egenskaper shouldHaveSize 3
    result.stedfesting shouldNotBe null
}
```

### Test Independence

Each test should be independent and not rely on other tests:

```kotlin
// Bad - tests depend on execution order
var sharedState: Int = 0

should("increment counter") {
    sharedState++
    sharedState shouldBe 1
}

should("increment counter again") { // Depends on previous test!
    sharedState++
    sharedState shouldBe 2
}

// Good - each test is independent
should("increment counter from zero") {
    var counter = 0
    counter++
    counter shouldBe 1
}

should("increment counter multiple times") {
    var counter = 0
    counter++
    counter++
    counter shouldBe 2
}
```

### Setup and Teardown

Use Kotest lifecycle hooks:

```kotlin
class MyTest : ShouldSpec({
    lateinit var rocksDbContext: RocksDbContext

    beforeEach {
        rocksDbContext = RocksDbContext(dbPath = "test-${UUID.randomUUID()}.db")
    }

    afterEach {
        rocksDbContext.close()
        File(rocksDbContext.dbPath).deleteRecursively()
    }

    should("test something") {
        // Use rocksDbContext
    }
})
```

**Lifecycle hooks:**

- `beforeSpec` / `afterSpec` - Before/after all tests
- `beforeEach` / `afterEach` - Before/after each test
- `beforeContainer` / `afterContainer` - Before/after nested contexts

### Avoid Testing Private Methods

From `CLAUDE.md`:

> When there is a need to unit-test a private method, make it public instead of using reflection to access it.

Make the method internal or public, or test it through public APIs.

### Don't Simplify Tests to Make Them Pass

From `CLAUDE.md`:

> When writing tests, don't try to please me by making tests pass by simplifying and taking shortcuts. If something stops you from writing proper tests, stop and ask me for help.

Write tests that assert the correct behavior, even if the implementation is not yet complete.

## Debugging Tests

### IntelliJ IDEA

1. Right-click test class or method
2. Select "Debug 'TestName'"
3. Set breakpoints in test or source code

### Command Line

```bash
./gradlew test --debug-jvm
```

Then attach debugger to port 5005.

### Print Debugging

```kotlin
should("debug geometry calculation") {
    val geometry = calculateGeometry(stedfesting)

    println("Geometry: $geometry")
    println("Length: ${geometry.length}")
    println("SRID: ${geometry.srid}")

    geometry shouldNotBe null
}
```

**Tip:** Use `--info` flag to see println output:

```bash
./gradlew test --info --tests="GeometryTest"
```

## Coverage

### Run Tests with Coverage

IntelliJ IDEA:

1. Right-click on `src/test/kotlin`
2. Select "Run 'Tests in tnits-generator' with Coverage"

### Gradle (JaCoCo)

Add to `build.gradle.kts`:

```kotlin
plugins {
    jacoco
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        html.required.set(true)
        xml.required.set(false)
        csv.required.set(false)
    }
}
```

Run:

```bash
./gradlew test jacocoTestReport
```

Report: `build/reports/jacoco/test/html/index.html`

## Common Testing Patterns

### Testing Exceptions

```kotlin
should("throw exception for invalid input") {
    shouldThrow<IllegalArgumentException> {
        parseWkt("INVALID WKT", SRID.UTM33)
    }
}

should("throw exception with specific message") {
    val exception = shouldThrow<IllegalStateException> {
        service.process(invalidData)
    }
    exception.message shouldContain "Invalid state"
}
```

### Testing Async Code

```kotlin
should("process data asynchronously") {
    val result = runBlocking {
        service.fetchDataAsync()
    }

    result shouldNotBe null
}
```

### Testing Sequences

```kotlin
should("stream data lazily") {
    val sequence = rocksDbContext.streamEntriesByPrefix(
        ColumnFamily.VEGLENKER,
        prefix
    )

    val results = sequence.take(10).toList()

    results shouldHaveSize 10
    results.first() shouldNotBe null
}
```

### Parameterized Tests

```kotlin
listOf(
    Triple(0.0, 1.0, 100.0),
    Triple(0.0, 0.5, 50.0),
    Triple(0.25, 0.75, 50.0)
).forEach { (start, end, expectedLength) ->
    should("calculate correct length for range $start to $end") {
        val result = calculateLength(start, end, totalLength = 100.0)
        result shouldBe (expectedLength plusOrMinus 0.01)
    }
}
```

## Troubleshooting

### Tests Not Running

**Problem:** Kotest tests not discovered

**Solution:** Ensure `kotest-runner-junit5` is in dependencies:

```kotlin
testImplementation("io.kotest:kotest-runner-junit5:6.0.0")
```

### RocksDB Conflicts

**Problem:** Database locked by another test

**Solution:** Use unique database paths per test:

```kotlin
val dbPath = "test-db-${UUID.randomUUID()}"
```

### TestContainers Port Conflicts

**Problem:** Port already in use

**Solution:** Stop conflicting containers:

```bash
docker stop $(docker ps -aq)
```

### Test Filtering Not Working

**Problem:** `kotest_filter_tests` doesn't work

**Solution:** Check syntax:

```bash
# Correct
kotest_filter_tests="*pattern*" ./gradlew test --tests="ClassName"

# Wrong
./gradlew test --tests="*pattern*"  # Doesn't work with Kotest
```

## Related Documentation

- [Getting Started](GETTING_STARTED.md) - Setup and first run
- [Architecture Overview](ARCHITECTURE.md) - System design
- [Concepts Glossary](CONCEPTS.md) - Domain terminology
- [Kotest Documentation](https://kotest.io) - Official Kotest docs
