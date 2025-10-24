# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin CLI application for synchronizing road network data from the Norwegian Road Database (NVDB) and exporting it in TN-ITS format. The application uses clean architecture principles with RocksDB for storage and Koin for dependency injection.

**For comprehensive documentation, see:**
- [Architecture Overview](docs/ARCHITECTURE.md) - System design and structure
- [Getting Started](docs/GETTING_STARTED.md) - Setup and first run
- [Concepts Glossary](docs/CONCEPTS.md) - Domain terminology
- [Data Flow](docs/DATA_FLOW.md) - Processing pipeline
- [Storage Architecture](docs/STORAGE.md) - RocksDB details
- [TN-ITS Export](docs/TNITS_EXPORT.md) - Export functionality
- [Testing Guide](docs/TESTING.md) - Testing conventions
- [Koin Dependency Injection](docs/KOIN_DEPENDENCY_INJECTION.md) - DI with Koin annotations

## Quick Start

### CLI Commands

```bash
# Run automatic mode (decides between snapshot/update)
./gradlew run

# Generate full snapshot
./gradlew run --args="snapshot"

# Generate delta update
./gradlew run --args="update"

# Export INSPIRE RoadNet
./gradlew run --args="inspire-roadnet"
```

### Dependency Injection

The application uses **Koin with annotations** for dependency injection:
- `@Singleton` - Register services and repositories
- `@Named("name")` - Distinguish multiple instances of same type
- `@KoinApplication` - Automatic module discovery and initialization
- See [Koin Dependency Injection Guide](docs/KOIN_DEPENDENCY_INJECTION.md) for details

### Logging

Use SLF4J logging via `WithLogger` interface:

```kotlin
class MyService : WithLogger {
  fun doWork() {
    log.info("Starting work")
    log.measure("Processing data") {
      // work here
    }
  }
}
```

## Development Commands

### Build and Test

```bash
./gradlew build          # Full build including tests
./gradlew test           # Run tests only
./gradlew run            # Run application (auto mode by default)
```

### Running Specific Tests

```bash
kotest_filter_tests="*.generateS3Key should format with padded vegobjekttype for speed limits" ./gradlew test --info --rerun --tests="SpeedLimitExporterTest"
```

**Important test filtering rules:**
- NEVER use asterisks (*) or wildcards in `--tests` parameter
- ALWAYS use `--tests` with exact class names only (e.g., `--tests="ClassName"`)
- For test name filtering, use `kotest_filter_tests` environment variable with wildcards
- Pattern: `kotest_filter_tests="*test name pattern*" ./gradlew test --tests="ExactClassName"`

See [Testing Guide](docs/TESTING.md) for comprehensive testing documentation.

### Code Formatting

```bash
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Format code automatically
./gradlew installGitHooks    # Install pre-commit hook
```

### API Model Generation

```bash
./gradlew generateAllApiModels  # Generate models from OpenAPI specs
```

## NVDB API Usage

### Fetching Data for Tests

**IMPORTANT:** All fetched NVDB data must be saved to `tnits-generator/src/test/resources/`

```bash
# Fetch a vegobjekt by type and ID
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/323113504?inkluder=alle" | jq > tnits-generator/src/test/resources/vegobjekt-105-323113504.json

# Extract veglenkesekvens IDs from vegobjekt (IMPORTANT: IDs are in stedfesting.linjer, NOT lokasjon)
IDS=$(jq -r '.stedfesting.linjer[].id' tnits-generator/src/test/resources/vegobjekt-105-323113504.json | paste -sd, -)

# Fetch related veglenkesekvenser
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=$IDS" | jq > tnits-generator/src/test/resources/veglenkesekvenser-$(echo $IDS | cut -d, -f1)-$(echo $IDS | rev | cut -d, -f1 | rev).json
```

### Test Resource Naming

- Vegobjekter: `vegobjekt-<type>-<id>.json`
- Multiple veglenkesekvenser: `veglenkesekvenser-<firstId>-<lastId>.json`
- Single veglenkesekvens: `veglenkesekvens-<id>.json`
- Always format JSON with `jq`
- Always save to `tnits-generator/src/test/resources/`

See [Concepts Glossary](docs/CONCEPTS.md) for NVDB domain terminology.
- When writing test with ShouldSpec, 'should' will automatically be inserted as the first part of the test name