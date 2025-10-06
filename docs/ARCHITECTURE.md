# Architecture Overview

This document provides a high-level overview of the nvdb-tnits system architecture.

## System Purpose

The system synchronizes road network data from the Norwegian Road Database (NVDB) and exports it in TN-ITS (Transport Network - Intelligent Transport Systems) format for European data exchange.

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         NVDB APIs                                │
│  ┌─────────────────┐              ┌──────────────────┐           │
│  │  Uberiket API   │              │ Datakatalog API  │           │
│  │ (Road Network)  │              │   (Metadata)     │           │
│  └─────────────────┘              └──────────────────┘           │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ HTTP/REST
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    tnits-generator (CLI)                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Application.kt - Main entry point with CLI commands       │  │
│  │  • snapshot  - Generate full TN-ITS snapshot               │  │
│  │  • update    - Generate delta update                       │  │
│  │  • auto      - Automatic mode (default)                    │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐      │
│  │  handlers/  │  │  vegnett/    │  │  vegobjekter/       │      │
│  │  Orchestrate│  │  Road network│  │  Road objects       │      │
│  │  workflows  │  │  logic       │  │  (speed limits)     │      │
│  └─────────────┘  └──────────────┘  └─────────────────────┘      │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │  storage/ - RocksDB abstraction layer                   │     │
│  │  • RocksDbContext - Database connection management      │     │
│  │  • VeglenkerRocksDbStore - Road network storage         │     │
│  │  • VegobjekterRocksDbStore - Road objects storage       │     │
│  │  • DirtyCheckingRocksDbStore - Change tracking          │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │  TnitsFeatureExporter - XML generation and export       │     │
│  │  • Streaming XML generation via XmlStreamDsl            │     │
│  │  • Parallel processing with worker pools                │     │
│  │  • S3/MinIO or local file output                        │     │
│  └─────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │       RocksDB Storage           │
            │  veglenker.db/ (local disk)     │
            │  • Column Families:             │
            │    - VEGLENKER                  │
            │    - VEGOBJEKTER                │
            │    - DIRTY_VEGLENKESEKVENSER    │
            │    - DIRTY_VEGOBJEKTER          │
            │    - KEY_VALUE                  │
            └─────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │      S3/MinIO Storage           │
            │  • RocksDB backups              │
            │  • TN-ITS XML exports           │
            │    - snapshots/                 │
            │    - updates/                   │
            └─────────────────────────────────┘
                              │
                              ▼
            ┌─────────────────────────────────┐
            │    tnits-katalog (REST API)     │
            │  Spring Boot service serving    │
            │  exported TN-ITS files          │
            └─────────────────────────────────┘
```

## Module Structure

### tnits-generator

The core CLI application responsible for data synchronization and export.

**Key responsibilities:**

- Fetch road network data from NVDB Uberiket API
- Store data efficiently in RocksDB
- Track changes and dirty state
- Generate TN-ITS XML exports (snapshots and updates)
- Manage S3 backups and exports

**Main entry point:** `Application.kt:22`

**Key packages:**

- `config/` - Configuration loading (Hoplite)
- `gateways/` - API clients for NVDB services
- `handlers/` - Business logic orchestration
- `storage/` - RocksDB persistence layer
- `vegnett/` - Road network domain logic
- `vegobjekter/` - Road objects domain logic
- `openlr/` - OpenLR encoding for location referencing
- `geometry/` - Geospatial transformations
- `xml/` - Streaming XML generation DSL

### tnits-katalog

Spring Boot REST service that serves exported TN-ITS files from MinIO/S3.

**Key responsibilities:**

- Provide HTTP access to generated TN-ITS files
- List available snapshots and updates
- Stream files directly from S3 storage

## Technology Stack

| Technology           | Purpose                        | Documentation                                                            |
|----------------------|--------------------------------|--------------------------------------------------------------------------|
| **Kotlin**           | Primary language               | [kotlin-lang.org](https://kotlinlang.org)                                |
| **Gradle**           | Build system                   | [gradle.org](https://gradle.org)                                         |
| **RocksDB**          | Embedded key-value storage     | [rocksdb.org](https://rocksdb.org)                                       |
| **Protocol Buffers** | Binary serialization           | [protobuf.dev](https://protobuf.dev)                                     |
| **Ktor Client**      | HTTP client for NVDB APIs      | [ktor.io/client](https://ktor.io/docs/client.html)                       |
| **Clikt**            | CLI argument parsing           | [github.com/ajalt/clikt](https://github.com/ajalt/clikt)                 |
| **JTS**              | Geospatial geometry operations | [locationtech.org/jts](https://locationtech.org/projects/jts)            |
| **GeoTools**         | Coordinate transformations     | [geotools.org](https://geotools.org)                                     |
| **OpenLR**           | Location referencing encoding  | [openlr.org](https://www.openlr.org)                                     |
| **MinIO SDK**        | S3-compatible object storage   | [min.io](https://min.io)                                                 |
| **Kotest**           | Testing framework              | [kotest.io](https://kotest.io)                                           |
| **Testcontainers**   | Integration testing            | [testcontainers.org](https://testcontainers.org)                         |
| **Spring Boot**      | katalog REST service           | [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) |

## Data Flow

The system operates in three main phases:

### 1. Initial Backfill

```
NVDB API → Fetch all road network → Store in RocksDB
          ↓
       Fetch all road objects → Store in RocksDB
```

Implemented in: `handlers/PerformBackfillHandler.kt`

### 2. Incremental Updates

```
NVDB API → Fetch change events → Update RocksDB → Mark dirty
```

Implemented in: `handlers/PerformUpdateHandler.kt`

### 3. TN-ITS Export

```
RocksDB → Process dirty items → Generate XML → Upload to S3
```

Implemented in: `TnitsFeatureExporter.kt`, `handlers/ExportUpdateHandler.kt`

## Key Design Patterns

### Dependency Injection via Services

All services are initialized in a single composition root (`Services.kt:46`) using constructor injection. The `withServices` block ensures proper resource cleanup.

**Example:**

```kotlin
Services().use { services ->
  services.tnitsFeatureExporter.exportSnapshot(...)
}
```

### Repository Pattern

Storage operations are abstracted through repository interfaces:

- `VeglenkerRepository` - Road network data
- `VegobjekterRepository` - Road objects data
- `DirtyCheckingRepository` - Change tracking

### Unit-of-Work for Atomicity

Batch operations use the `writeBatch` context for atomic multi-operation transactions:

```kotlin
rocksDbContext.writeBatch {
  veglenkerStore.batchUpdate(updates)
  dirtyCheckingStore.markDirty(ids)
}
```

See: `storage/WriteBatchContext.kt`

### Streaming Processing

Large XML exports use streaming to avoid memory issues:

- XML streaming via StAX (`xml/XmlStreamDsl.kt`)
- Parallel processing with worker pools
- Direct streaming to S3 via `S3OutputStream`

## Configuration

Configuration is loaded from `application.conf` with environment variable overrides using Hoplite.

**Configuration structure:**

```hocon
uberiketApi {
  baseUrl = "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/"
}

s3 {
  endpoint = "http://localhost:9000"
  accessKey = user
  secretKey = password
}

exporter {
  gzip = true
  target = S3
  bucket = nvdb-tnits-local-data-01
}

backup {
  enabled = true
  bucket = nvdb-tnits-local-data-01
  path = rocksdb-backup
}
```

See: `config/AppConfig.kt:12`

## Performance Considerations

### RocksDB Optimization

- LZ4 compression for storage efficiency
- Column families for logical data separation
- Bulk loading configuration for initial backfill
- Batch operations for write efficiency

### Parallel Processing

- Multi-threaded speed limit processing
- Worker pool architecture with step-lock orchestration
- Ordered output via post-processing sort

See: `TnitsFeatureExporter.kt` for parallel processing implementation

### Memory Management

- Streaming XML generation (no DOM)
- Lazy sequence processing for large datasets
- Protocol Buffers for compact serialization

## Related Documentation

- [Getting Started Guide](GETTING_STARTED.md) - Setup and first run
- [Storage Architecture](STORAGE.md) - RocksDB details
- [Data Flow](DATA_FLOW.md) - Processing pipeline
- [TN-ITS Export](TNITS_EXPORT.md) - Export functionality
- [Testing Guide](TESTING.md) - Test structure and execution
- [Concepts Glossary](CONCEPTS.md) - Domain terminology
