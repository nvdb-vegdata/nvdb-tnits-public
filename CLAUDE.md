# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin prototype for exporting TN-ITS (Transport Network - Intelligent Transport Systems) daily changes for speed limits from the Norwegian Road Database (NVDB). The application provides REST APIs to:

1. Load initial roadnet and speed limit data from NVDB
2. Generate full TN-ITS compliant snapshots of speed limits
3. Create incremental daily snapshots showing changes since a specific date
4. Convert location references to OpenLR format for TN-ITS compliance

## Architecture

The application follows a layered architecture using Ktor as the web framework and Exposed ORM for database operations:

- **Application.kt**: Main entry point that configures Ktor server on port 8080
- **config/**: Configuration modules for serialization, database, and routing setup
- **database/**: Exposed ORM table definitions and entity classes for roadnet and speed limits
- **routes/**: REST API route handlers for health checks and main API endpoints
- **service/**: Business logic layer containing NvdbService (data loading) and TnItsService (snapshot generation)

### Database Schema

Two main tables store the road network data:
- `RoadnetTable`: Basic road network segments with geometry and administrative data
- `SpeedLimitTable`: Speed limit objects with NVDB IDs, OpenLR references, and validity periods

Both tables include change tracking fields (`createdAt`, `modifiedAt`) to support incremental updates.

### Key REST Endpoints

- `POST /api/initial-load`: Loads initial roadnet and speed limit data
- `GET /api/snapshot/full`: Generates complete TN-ITS speed limit snapshot
- `GET /api/snapshot/daily/{date}`: Generates incremental changes since specified date
- `GET /health`: Health check endpoint

## Development Commands

### Build and Test
```bash
./gradlew build          # Full build including tests
./gradlew test           # Run tests only
./gradlew run            # Start the application (http://localhost:8080)
```

### Database Configuration

The application supports both H2 (development) and PostgreSQL (production) databases. Configuration is handled through environment variables:
- `DATABASE_URL`: JDBC connection string
- `DATABASE_USER`: Database username  
- `DATABASE_PASSWORD`: Database password

If not set, defaults to H2 in-memory database for development.

### OpenAPI Client Generation

The project includes OpenAPI client generation from NVDB's API specification:
```bash
./gradlew openApiGenerate    # Generate client from nvdb-api.json
```

**Note**: The generated client is currently disabled in the build due to compatibility issues with the Kotlin serialization library. The generation works but integration is pending resolution of serialization conflicts.

### Testing

Tests use Kotest framework and can be run individually or as a suite. The application includes integration tests that start the full Ktor server for endpoint testing.

## Important Implementation Notes

- **Exposed ORM Version**: Uses stable version 0.44.1 due to compatibility issues with beta versions
- **OpenLR Integration**: Currently uses placeholder OpenLR encoding - real implementation would require an OpenLR library
- **Change Detection**: Uses database timestamps for incremental updates rather than NVDB's native change tracking
- **TN-ITS Compliance**: Implements basic TN-ITS data structure but may need refinement for full compliance

The codebase is structured to allow easy extension with additional road object types beyond speed limits.