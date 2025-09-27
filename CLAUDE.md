# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin CLI application prototype for synchronizing road network data (veglenker) from the Norwegian Road Database (NVDB) using pure RocksDB storage. The application performs:

1. Initial backfill of all road network data from NVDB
2. Incremental updates by processing change events from NVDB's event stream
3. High-performance data storage using RocksDB with Protocol Buffers serialization
4. RocksDB backup and restore functionality to/from S3-compatible storage
5. Exporting speed limits and other road objects for TN-ITS compliance

## Architecture

The application follows a CLI-based architecture with RocksDB as the primary storage layer:

- **Application.kt**: Main entry point with Clikt-based CLI commands for data synchronization and TN-ITS export
- **config/**: Configuration modules for S3, backup, and application settings
- **geometry/**: Spatial geometry processing utilities for coordinate transformation and intersection calculations
- **extensions/**: Utility extension functions for common operations
- **model/**: Domain models including road segments and positioning
- **vegnett/**: Core business logic for road network data backfilling and incremental updates
- **services/**: API client services for communicating with NVDB's Uberiket API and Datakatalogen
- **storage/**: High-performance storage layer with RocksDB-based veglenker caching

### RocksDB Storage Schema

The application uses RocksDB column families for organizing data:

- **DEFAULT**: Default column family for general key-value operations
- **KEY_VALUE**: Application state tracking (backfill progress, last processed event IDs, timestamps)
- **VEGLENKER**: Road network links (veglenker) with NVDB IDs, link numbers, and full road data
- **VEGOBJEKTER**: Road objects like speed limits with positioning data
- **DIRTY_VEGLENKESEKVENSER**: Tracks dirty veglenkesekvens IDs for incremental processing
- **DIRTY_VEGOBJEKTER**: Tracks dirty vegobjekt IDs by type for incremental processing

All data uses Protocol Buffers serialization for efficient storage and retrieval.

### Dirty Checking System

The application includes a sophisticated dirty checking system for efficient incremental processing:

- **DirtyCheckingRepository**: Interface for managing dirty state tracking
- **DirtyCheckingRocksDbStore**: RocksDB-based implementation for dirty state management
- **Dirty Vegobjekt Tracking**: Tracks changed vegobjekter by type using `DIRTY_VEGOBJEKTER` column family
- **Stedfesting Relationship Queries**: Finds vegobjekter positioned on dirty veglenkesekvenser
- **Batch Clear Operations**: Efficiently clears dirty state after processing

The dirty checking system enables efficient delta processing for TN-ITS exports by tracking only changed data.

### CLI Application Architecture

The application uses **Clikt 5.0.3** for command-line interface parsing with the following structure:

#### CLI Implementation Details

- **Main Command**: `NvdbTnitsApp` extends `CliktCommand` and coordinates subcommands
- **Base Command**: `BaseCommand` abstract class provides shared functionality for commands that need service initialization and backup
- **Dependency**: `com.github.ajalt.clikt:clikt:5.0.3` added to build.gradle.kts
- **Coroutine Support**: Uses `runBlocking` to bridge between Clikt's synchronous command execution and the application's suspend functions
- **Service Integration**: Preserves existing `Services` dependency injection pattern for business logic

#### Main CLI Commands

- **`nvdb-tnits snapshot`**: Generate TN-ITS speed limit full snapshot
- **`nvdb-tnits update`**: Generate TN-ITS speed limit delta update
- **`nvdb-tnits auto`**: Automatic mode based on configuration (TODO: not yet implemented)

#### Command Options

- **`--help`**: Available on all commands for usage information

#### Application Flow

1. **Startup**: Restore RocksDB backup from S3 if available and database is empty
2. **Backfill**: If first run, download all road network data from NVDB
3. **Incremental Updates**: Process change events from NVDB's event stream
4. **Command Execution**: Execute the specified CLI command
5. **Automatic Backup**: After successful `snapshot` or `update` operations (unless `--no-backup` is specified)

### S3/MinIO Export

The application now supports exporting TN-ITS XML files directly to S3-compatible storage (MinIO):

- **S3 Configuration**: Add S3 settings to `AppConfig` with endpoint, bucket, credentials
- **Automatic Fallback**: Falls back to local file export if S3 is unavailable
- **Streaming Upload**: Uses `S3OutputStream` with piped streams for memory-efficient uploads
- **Folder Structure**: Files are organized as `{typeId-padded}-${typeName}/{timestamp}/{type}.xml[.gz]`
  - Example: `0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz`
  - Speed limits use vegobjekttype 105, resulting in `0105-speedLimit/` folder
- **GZIP Support**: Maintains GZIP compression when uploading to S3

### Logging System

The application uses SLF4J logging with the `WithLogger` interface pattern:

- **Interface Pattern**: Classes implement `WithLogger` interface to get `log` property
- **Logger Creation**: Uses `LoggerFactory.getLogger()` with proper class detection
- **Log Levels**: Standard SLF4J levels (trace, debug, info, warn, error)
- **Syntax**: Use `log.info("message")` or `log.error("message", exception)` - no block syntax
- **Performance**: Includes `measure` extension for timing operations with automatic logging

Example usage:

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
./gradlew run            # Show CLI help (no arguments)
```

### CLI Usage

```bash
# Show main help
./gradlew run

# Generate full snapshot
./gradlew run --args="snapshot"

# Generate delta update
./gradlew run --args="update"

# Create backup only
./gradlew run --args="backup"

# Auto mode (TODO: not implemented)
./gradlew run --args="auto"

# Skip automatic backup after snapshot
./gradlew run --args="snapshot --no-backup"

# Show help for specific command
./gradlew run --args="snapshot --help"
```

#### Running specific tests

```bash
kotest_filter_tests="*.generateS3Key should format with padded vegobjekttype for speed limits" ./gradlew test --info --rerun --tests="SpeedLimitExporterTest"
```

- The full test name is `<package>.<Class>.<test name>`
- Wildcards at the start in combination with `--tests` ensures we only run the specified test
- By using `--info` and `--rerun`, you get detailed output and rerun tests even if they are up-to-date
- You can also use wildcards in `kotest_filter_tests` for more flexible filtering
- For specific tests, always use `--tests` to specify the test class to run; this narrows down the test scope
- IMPORTANT: NEVER USE `--tests` with wildcards, as this doesn't work properly with Kotest filtering. Instead use `kotest_filter_tests` for wildcards.
- NEVER use asterisks (*) or any wildcards directly in --tests parameter
- ALWAYS use --tests with exact class names only (e.g., --tests="ClassName")
- For test name filtering, ALWAYS use kotest_filter_tests environment variable with wildcards
- When debugging specific tests, use the pattern: kotest_filter_tests="*test name pattern*" ./gradlew test --tests="ExactClassName"

### RocksDB Configuration

The application uses RocksDB as its primary storage with no SQL database dependencies. Configuration includes:

- **Storage Location**: `veglenker.db/` directory in project root
- **LZ4 Compression**: Enabled by default for optimal storage efficiency
- **Column Families**: Automatically created and managed through `ColumnFamily` enum
- **Backup/Restore**: Integrated S3 backup using RocksDB's native BackupEngine

### OpenAPI Client Generation

The project includes OpenAPI client generation from several API specifications:

```bash
./gradlew generateAllApiModels
```

### Testing

Tests use Kotest 6.0 framework and can be run individually or as a suite. The application includes unit tests for the CLI application logic.

#### Running Tests

**Run all tests:**

```bash
./gradlew test           # Run all tests
```

**Run specific test classes:**

```bash
./gradlew test --tests='XmlStreamDslTest'                               # Run by class name pattern
./gradlew test --tests='no.vegvesen.nvdb.tnits.xml.XmlStreamDslTest'    # Run by full qualified class name
./gradlew test --tests='no.vegvesen.nvdb.tnits.geometry.*'             # Run all tests in package
```

#### Test Structure

Tests are placed in the matching package as the source code under `src/test/kotlin`. Test resources are placed under `src/test/resources`.

#### Testing Infrastructure

- **TempRocksDbConfig.withTempDb**: Utility for creating temporary RocksDB instances in tests
- **TestContainers**: Used for MinIO S3 integration testing
- **Kotest**: Test framework with StringSpec style for readable test names

The project uses Kotest's StringSpec style for readable test names and supports both JUnit Platform and Kotest-specific filtering.

### Code Style and Formatting

The project uses ktlint for code formatting and style checking. After cloning the repository, new team members should run:

```bash
./gradlew installGitHooks    # Install pre-commit hook for automatic formatting
```

This installs a git pre-commit hook that automatically:

- Runs `ktlint` formatting on all Kotlin files
- Stages any formatting changes
- Ensures code passes style checks before commit

You can also run ktlint manually:

```bash
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Format code automatically
```

## Performance Optimization

### Parallel Speed Limit Processing

The application implements high-performance parallel processing for speed limit generation:

- **ParallelSpeedLimitProcessor**: Multi-threaded processor using worker pool architecture
- **Steplock Orchestration**: Single orchestrator fetches ID batches and distributes work ranges to workers
- **Worker Count**: Automatically scales to CPU cores (`Runtime.getRuntime().availableProcessors()`)
- **Ordered Output**: Results are sorted by ID before streaming to maintain consistent output

### RocksDB Storage System

High-performance embedded storage for veglenker data using RocksDB with Protocol Buffers serialization:

- **Storage Location**: `veglenker.db/` directory in project root
- **Compression**: LZ4 compression enabled by default for optimal storage efficiency
- **Serialization**: Protocol Buffers (protobuf) for compact and efficient binary serialization
- **Batch Operations**: Optimized batch read/write operations for handling large datasets
- **Bulk Loading**: Configured for high-throughput bulk data loading operations
- **Thread Safety**: Safe for concurrent read operations across multiple threads
- **Column Families**: Uses typed column families (DEFAULT, NODER, VEGLENKER) for logical data separation

#### Storage Architecture:

- **RocksDbContext**: Core context managing database connection, column families, and providing utility methods
- **Repository Pattern**: Type-specific stores (`VeglenkerRocksDbStore`, `VegobjekterRocksDbStore`, `KeyValueRocksDbStore`, `DirtyCheckingRocksDbStore`)
- **RocksDbBackupService**: S3 backup and restore using RocksDB's native BackupEngine
- **Wrapper Methods**: All RocksDB operations go through type-safe wrapper methods that accept `ColumnFamily` enum
- **Encapsulation**: Raw RocksDB and ColumnFamilyHandle instances are fully encapsulated
- **Unit-of-Work**: Atomic batch operations via `writeBatch` method

#### Storage Interface:

- `get(veglenkesekvensId)`: Retrieve veglenker list for a specific sequence ID
- `batchGet(ids)`: Efficient batch retrieval for multiple sequence IDs (eliminates multiGetAsList complexity)
- `upsert(id, veglenker)`: Insert or update veglenker data
- `batchUpdate(updates)`: Atomic batch updates using BatchOperation sealed class
- `clear()`: Complete database reset and reinitialization

#### RocksDbContext Wrapper Methods:

- `get(columnFamily, key)`: Single key retrieval
- `put(columnFamily, key, value)`: Single key insertion/update
- `batchGet(columnFamily, keys)`: Batch retrieval with simplified API
- `writeBatch(columnFamily, operations)`: Type-safe batch operations using BatchOperation.Put/Delete
- `writeBatch(block)`: Unit-of-Work pattern for atomic cross-column-family operations
- `getBatch(columnFamily, keys)`: Efficient batch retrieval with simplified API
- `findByPrefix(columnFamily, prefix)`: Prefix-based key-value scanning
- `streamEntriesByPrefix(columnFamily, prefix)`: Memory-efficient streaming prefix scans
- `newIterator(columnFamily)`: Iterator for full data traversal
- `deleteRange(columnFamily, start, end)`: Range deletion operations

### Thread Safety

- **RocksDB Context**: Thread-safe initialization and cleanup with synchronized methods
- **Geometry Processing**: Uses per-operation WKBReader/WKBWriter instances for thread safety
- **Storage Access**: RocksDB supports concurrent reads, batch operations are atomic
- **Coroutines**: Leverages Kotlin coroutines for non-blocking parallel processing

### Backup and Restore

The application includes comprehensive backup functionality:

- **RocksDbBackupService**: Uses RocksDB's native BackupEngine for consistent snapshots
- **S3 Integration**: Automatic compression and upload to S3-compatible storage (MinIO)
- **Startup Restore**: Automatically restores from S3 backup if local database is empty
- **Manual Backup**: CLI backup command for on-demand backup creation
- **Atomic Operations**: Backup and restore operations ensure data consistency

## Important Implementation Notes

- **Storage-Only RocksDB**: No SQL database dependencies, pure RocksDB storage architecture
- **Protocol Buffers**: All data serialization uses kotlinx.serialization with protobuf for efficiency
- **TN-ITS Speed Limits**: Implemented with full XML export functionality including WGS84 coordinate transformation
- **Geometry Processing**: Advanced spatial calculations for road segment intersections and coordinate projections using JTS
- **OpenLR Integration**: Currently uses placeholder OpenLR encoding - real implementation would require an OpenLR library
- **Change Detection**: Uses NVDB's native event stream (veglenkesekvens hendelser) for incremental updates
- **Data Processing**: Implements backfill and incremental update patterns for large-scale data synchronization
- **State Management**: Tracks processing state using KeyValue table to support resumable operations
- **Embedded Storage**: RocksDB-based storage with LZ4 compression for optimal performance and data density
- **Serialization**: Uses kotlinx.serialization with Protocol Buffers for type-safe, efficient binary serialization
- **Unified Storage**: All data (application state, road network, objects) stored in RocksDB column families
- **RocksDB Encapsulation**: All RocksDB operations are encapsulated through wrapper methods in RocksDbContext
- **Type Safety**: Uses ColumnFamily enum instead of raw ColumnFamilyHandle instances for type-safe database operations
- **Simplified API**: Complex operations like multiGetAsList are abstracted away with clean batchGet wrapper methods

The CLI application includes TN-ITS speed limit export functionality and can be extended with additional road object types.

## NVDB API Usage

### Fetching Data

- use the format https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/85283803?inkluder=alle to fetch a vegobjekt with a given type and id
- use the format https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=41423,42424 to fetch multiple veglenkesekvenser

### Extracting Veglenkesekvenser from Vegobjekter

To find which veglenkesekvenser a vegobjekt references, extract the IDs from the `stedfesting` structure:

```bash
# Extract veglenkesekvens IDs from a vegobjekt
jq -r '.stedfesting.linjer[].id' vegobjekt-105-83589630.json

# Extract unique IDs from multiple vegobjekter
jq -r '.stedfesting.linjer[].id' vegobjekt-*.json | sort -u
```

The `stedfesting.linjer[].id` field contains the veglenkesekvens ID that the road object is positioned on. Each `linjer` entry represents a road segment with:

- `id`: The veglenkesekvens ID
- `startposisjon`: Start position along the segment (0.0 to 1.0)
- `sluttposisjon`: End position along the segment (0.0 to 1.0)
- `retning`: Direction ("MED" = with road direction, "MOT" = against road direction)

### Test Resources

- test resources are to be placed in src/test/resources
- save vegobjekter with the format `vegobjekt-<type>-<id>.json`
- save veglenkesekvenser with the format `veglenkesekvenser-<id1>-<idX>.json`, including all IDs.
- Use `veglenkesekvens-<id>.json` for single veglenkesekvens
- Format JSON when saving (pipe curl to jq)

## Other notes

- Always update CLAUDE.md after significant changes
