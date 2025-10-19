# Project Overview

## Purpose
NVDB TN-ITS Public is a Kotlin CLI application that synchronizes road network data from the Norwegian Road Database (NVDB) and exports it in TN-ITS and INSPIRE formats.

## Key Features
- **Data Synchronization**: Backfill + event-based updates from NVDB API
- **TN-ITS Export**: Exports road data in TN-ITS XML format
- **INSPIRE Export**: Exports road network data in INSPIRE format
- **Storage**: Uses RocksDB for efficient local storage
- **Modes**: Supports snapshot, update, automatic, and INSPIRE export modes

## Public Repository
This is a public repository that serves as a reference implementation for integrating with NVDB roadnet data using backfill and event-based updates.

## Documentation
Comprehensive documentation is available in the `docs/` directory covering architecture, data flow, storage, testing, and export formats.