# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin console application prototype for synchronizing road network data (veglenker) from the Norwegian Road Database (NVDB) to a local database. The application performs:

1. Initial backfill of all road network data from NVDB
2. Incremental updates by processing change events from NVDB's event stream
3. Data storage using Exposed ORM with support for H2 and PostgreSQL databases
4. Exporting speed limits and other road objects for TN-ITS compliance

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

The application supports both H2 (development) and PostgreSQL (production) databases. Configuration is handled through environment variables:

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
- `src/test/kotlin/no/vegvesen/nvdb/tnits/VeglenkerCacheTest.kt` - Kryo binary cache serialization and deserialization tests

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

### Kryo Caching System

Fast binary caching for veglenker data provides 5-15x performance improvement:

- **Cache Location**: `veglenker-cache.kryo` in project root
- **Smart Invalidation**: Compares cache file timestamp with database `sistEndret` timestamps
- **Automatic Fallback**: Falls back to database loading if cache fails or is stale
- **Binary Serialization**: Uses Kryo with Objenesis for efficient data class serialization
- **Performance Measurement**: Integrated timing functions track cache operations

#### Cache Functions:
- `loadVeglenkerWithCache()`: Main entry point with fallback logic
- `saveVeglenkerToCache()`: Binary serialization to disk
- `loadVeglenkerFromCache()`: Binary deserialization with error handling
- `getCacheFileTimestamp()`: Database timestamp checking for cache validation

### Thread Safety

- **Geometry Processing**: Uses per-operation WKBReader/WKBWriter instances for thread safety
- **Database Access**: Each worker performs independent database queries within transaction scope
- **Coroutines**: Leverages Kotlin coroutines for non-blocking parallel processing

## Important Implementation Notes

- **Exposed ORM Version**: Uses beta version 1.0.0-beta-5 for latest features
- **TN-ITS Speed Limits**: Implemented with full XML export functionality including WGS84 coordinate transformation
- **Geometry Processing**: Advanced spatial calculations for road segment intersections and coordinate projections
- **OpenLR Integration**: Currently uses placeholder OpenLR encoding - real implementation would require an OpenLR library
- **Change Detection**: Uses NVDB's native event stream (veglenkesekvens hendelser) for incremental updates
- **Data Processing**: Implements backfill and incremental update patterns for large-scale data synchronization
- **State Management**: Tracks processing state using KeyValue table to support resumable operations
- **Binary Caching**: Kryo-based serialization with Objenesis instantiation for data classes without no-arg constructors

The console application includes interactive TN-ITS speed limit export and can be extended with additional road object types.
