# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin console application prototype for synchronizing road network data (veglenker) from the Norwegian Road Database (NVDB) to a local database. The application performs:

1. Initial backfill of all road network data from NVDB
2. Incremental updates by processing change events from NVDB's event stream
3. Data storage using Exposed ORM with support for H2, PostgreSQL, and Oracle Database Free
4. High-performance veglenker caching using RocksDB with Protocol Buffers serialization
5. Exporting speed limits and other road objects for TN-ITS compliance

## Architecture

The application follows a console-based architecture with Exposed ORM for database operations:

- **Application.kt**: Main entry point with suspend main() function and interactive menu for data synchronization and TN-ITS export
- **config/**: Configuration modules for database setup and application settings
- **database/**: Exposed ORM table definitions and entity classes for road network data
- **geometry/**: Spatial geometry processing utilities for coordinate transformation and intersection calculations
- **extensions/**: Utility extension functions for common operations
- **model/**: Domain models including road segments and positioning
- **vegnett/**: Core business logic for road network data backfilling and incremental updates
- **services/**: API client services for communicating with NVDB's Uberiket API and Datakatalogen
- **storage/**: High-performance storage layer with RocksDB-based veglenker caching

### Database Schema

The main table stores road network data:

- `Veglenker`: Road network links (veglenker) with NVDB IDs, link numbers, and full road data
- `KeyValue`: Simple key-value store for tracking application state (backfill progress, last processed event IDs)
- `Stedfestinger`: Location references for road objects
- `Vegobjekter`: Road objects like speed limits

All tables include timestamp fields for change tracking.

### Console Application Flow

1. **Startup**: Check if initial backfill has been completed
2. **Backfill**: If first run, download all road network data from NVDB
3. **Incremental Updates**: Process change events from NVDB's event stream
4. **TN-ITS Export**: Interactive menu for generating speed limit exports in TN-ITS compliant XML format

## Development Commands

### Build and Test

```bash
./gradlew build          # Full build including tests
./gradlew test           # Run tests only
./gradlew run            # Start the console application
```

### Database Configuration

The application supports H2 (development), PostgreSQL, and Oracle Database Free (production) databases. Configuration is handled through environment variables:

- `DATABASE_URL`: JDBC connection string
- `DATABASE_USER`: Database username
- `DATABASE_PASSWORD`: Database password

If not set, defaults to H2 in-memory database for development.

### OpenAPI Client Generation

The project includes OpenAPI client generation from several API specifications:

```bash
./gradlew generateAllApiModels
```

### Testing

Tests use Kotest 6.0 framework and can be run individually or as a suite. The application includes unit tests for the console application logic.

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

- `src/test/kotlin/no/vegvesen/nvdb/tnits/xml/XmlStreamDslTest.kt` - XML streaming DSL tests
- `src/test/kotlin/no/vegvesen/nvdb/tnits/geometry/GeometryHelpersTest.kt` - Geometry projection and transformation tests
- `src/test/kotlin/no/vegvesen/nvdb/tnits/geometry/CalculateIntersectingGeometryTest.kt` - Geometry intersection calculation tests
- `src/test/kotlin/no/vegvesen/nvdb/tnits/storage/RocksDbVeglenkerStoreTest.kt` - RocksDB storage layer tests with Protocol Buffers serialization
- `src/test/kotlin/no/vegvesen/nvdb/tnits/model/JtsGeometrySerializerTest.kt` - JTS geometry serialization tests

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

#### Storage Interface:
- `RocksDbVeglenkerStore`: Main storage implementation with embedded RocksDB
- `get(veglenkesekvensId)`: Retrieve veglenker list for a specific sequence ID
- `batchGet(ids)`: Efficient batch retrieval for multiple sequence IDs
- `upsert(id, veglenker)`: Insert or update veglenker data
- `batchUpdate(updates)`: Atomic batch updates with WriteBatch optimization
- `clear()`: Complete database reset and reinitialization

### Thread Safety

- **Geometry Processing**: Uses per-operation WKBReader/WKBWriter instances for thread safety
- **Database Access**: Each worker performs independent database queries within transaction scope
- **Coroutines**: Leverages Kotlin coroutines for non-blocking parallel processing

## Important Implementation Notes

- **Exposed ORM Version**: Uses beta version 1.0.0-beta-5 for latest features
- **Database Support**: Supports Oracle Database Free (production), PostgreSQL, and H2 (development)
- **TN-ITS Speed Limits**: Implemented with full XML export functionality including WGS84 coordinate transformation
- **Geometry Processing**: Advanced spatial calculations for road segment intersections and coordinate projections using JTS
- **OpenLR Integration**: Currently uses placeholder OpenLR encoding - real implementation would require an OpenLR library
- **Change Detection**: Uses NVDB's native event stream (veglenkesekvens hendelser) for incremental updates
- **Data Processing**: Implements backfill and incremental update patterns for large-scale data synchronization
- **State Management**: Tracks processing state using KeyValue table to support resumable operations
- **Embedded Storage**: RocksDB-based storage with LZ4 compression for optimal performance and data density
- **Serialization**: Uses kotlinx.serialization with Protocol Buffers for type-safe, efficient binary serialization
- **Storage Architecture**: Separates ephemeral road network data (RocksDB) from persistent application state (SQL database)

The console application includes interactive TN-ITS speed limit export and can be extended with additional road object types.
- use the format https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/85283803/2?inkluder=alle to fetch a vegobjekt with a given type, id and version
- use the format https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=41423,42424 to fetch multiple veglenkesekvenser