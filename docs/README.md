# NVDB TN-ITS Documentation

Complete documentation for the nvdb-tnits system - a Kotlin CLI application for synchronizing Norwegian road network data and exporting it in TN-ITS and INSPIRE formats.

## Getting Started

### [Getting Started Guide](GETTING_STARTED.md)

Setup instructions, prerequisites, first run, and common development tasks. Start here if you're new to the project.

### [FAQ](FAQ.md)

Frequently asked questions about data processing, backfill, dirty tracking, RocksDB management, and adding new features.

## Understanding the System

### [Concepts Glossary](CONCEPTS.md)

Comprehensive glossary of NVDB, TN-ITS, geospatial, and architectural concepts. Essential reference for understanding domain terminology.

### [Architecture Overview](ARCHITECTURE.md)

High-level system design, module structure, clean architecture layers, technology stack, and key design patterns.

### [Data Flow](DATA_FLOW.md)

How data flows through the system from NVDB APIs to TN-ITS exports, including backfill, incremental updates, and export phases.

## Core Components

### [Storage Architecture](STORAGE.md)

RocksDB column families, storage abstraction layer, serialization with Protocol Buffers, batch operations, and dirty checking.

### [Koin Dependency Injection](KOIN_DEPENDENCY_INJECTION.md)

Dependency injection using Koin with Jakarta annotations, clean architecture integration, and best practices.

### [Memory Optimization](MEMORY_OPTIMIZATION.md)

Four-phase optimization guide reducing CachedVegnett memory usage by 72%, including lazy computation, ID-based storage, and batch loading.

## Export Functionality

### [TN-ITS Export](TNITS_EXPORT.md)

TN-ITS snapshot and update exports, data mapping, OpenLR encoding, geometry handling, and S3 configuration.

### [INSPIRE RoadNet Export](INSPIRE_ROADNET_EXPORT.md)

INSPIRE RoadLink export for the complete Norwegian road network in WFS 2.0 format with ETRS89/UTM33 coordinates.

## Development

### [Testing Guide](TESTING.md)

Testing framework (Kotest), test structure, running tests, assertions, test utilities, and best practices.

## Quick Reference

| Document                                      | When to Read                                              |
|-----------------------------------------------|-----------------------------------------------------------|
| [Getting Started](GETTING_STARTED.md)         | Setting up the project for the first time                 | 
| [Concepts](CONCEPTS.md)                       | Need to understand NVDB or TN-ITS terminology             |
| [Architecture](ARCHITECTURE.md)               | Understanding system design and module structure          |
| [Data Flow](DATA_FLOW.md)                     | Following how data moves through the system               |
| [Storage](STORAGE.md)                         | Working with RocksDB or implementing repositories         |
| [Koin DI](KOIN_DEPENDENCY_INJECTION.md)       | Adding new services or understanding dependency injection |
| [TN-ITS Export](TNITS_EXPORT.md)              | Working on speed limit or traffic regulation exports      |
| [INSPIRE Export](INSPIRE_ROADNET_EXPORT.md)   | Working on road network geometry exports                  |
| [Testing](TESTING.md)                         | Writing or debugging tests                                |
| [Memory Optimization](MEMORY_OPTIMIZATION.md) | Understanding CachedVegnett optimizations                 |
| [FAQ](FAQ.md)                                 | Quick answers to common questions                         |

## Documentation Conventions

- **Mermaid diagrams:** Use [IntelliJ Mermaid Plugin](https://plugins.jetbrains.com/plugin/20146-mermaid) to view diagrams in the IDE
- **Code references:** File paths and line numbers are indicated as `path/to/file.kt:123`
- **Example data:** Test resources follow naming conventions in [Testing Guide](TESTING.md)
- **NVDB API examples:** Use `jq` to format JSON output

## Additional Resources

- [Main README](../README.md) - Norwegian project overview
- [CLAUDE.md](../CLAUDE.md) - Guidelines for Code assistants
- [NVDB API Documentation](https://nvdbapiles.atlas.vegvesen.no/)
- [TN-ITS Specification](https://spec.tn-its.eu)
- [INSPIRE Transport Networks](https://inspire.ec.europa.eu/id/document/tg/tn)
