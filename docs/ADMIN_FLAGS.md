# Admin Flags

This document describes the admin flags system that enables remote control of the tnits-generator's data state through the tnits-katalog REST API.

Diagrams are written in [Mermaid](https://mermaid.js.org) syntax; use [IntelliJ Mermaid Plugin](https://plugins.jetbrains.com/plugin/20146-mermaid) to view them in the IDE.

## Overview

The admin flags system allows administrators to remotely trigger data resets in the tnits-generator by setting flags through the tnits-katalog REST API. Flags are stored in S3/MinIO and read by the generator on startup.

### Purpose

Admin flags enable:

- **Data corruption recovery** - Reset database when data becomes corrupted
- **Force full re-sync** - Clear specific data types to trigger complete refresh from NVDB
- **Operational maintenance** - Reset road network or feature types independently
- **Testing workflows** - Quickly reset specific parts of the system during development

### Architecture

Flags provide a bridge between the katalog (REST API) and generator (CLI) services:

1. **tnits-katalog** (REST API) - Sets flags through authenticated endpoint
2. **S3/MinIO** (Object Store) - Persists flags as JSON files
3. **tnits-generator** (CLI) - Reads and processes flags on startup

## System Architecture

```mermaid
flowchart TB
    subgraph Admin["Administrator"]
        USER["Admin User"]
    end

    subgraph Katalog["tnits-katalog (REST API)"]
        API["AdminController<br/>POST /api/v1/admin/flags"]
        STORE["SharedKeyValueStore<br/>(S3KeyValueStore)"]
    end

    subgraph S3["S3/MinIO Storage"]
        FLAGS["admin-flags/<br/>• ResetDb<br/>• ResetRoadnet<br/>• ResetFeatureTypes"]
        BACKUP["rocksdb-backup/<br/>rocksdb-backup.tar.gz"]
    end

    subgraph Generator["tnits-generator (CLI)"]
        STARTUP["Application Startup"]
        RESTORE["RocksDbS3BackupService<br/>restoreIfNeeded()"]
        PROCESS["Process Admin Flags:<br/>1. Check RESET_DB<br/>2. Restore from backup<br/>3. Check RESET_ROADNET<br/>4. Check RESET_FEATURE_TYPES"]
        ROCKS[("RocksDB<br/>veglenker.db/")]
    end

    USER -->|" 1. POST /admin/flags "| API
    API -->|" 2. putValue() "| STORE
    STORE -->|" 3. PUT JSON "| FLAGS
    STARTUP -->|" On startup "| RESTORE
    RESTORE -->|" 4. getValue() "| STORE
    STORE -->|" 5. GET JSON "| FLAGS
    RESTORE -->|" 6. Process flags "| PROCESS
    PROCESS -->|" 7. Modify data "| ROCKS
    RESTORE -.->|" Download if needed "| BACKUP
    RESTORE -.->|" Upload on success "| BACKUP
    RESTORE -.->|" Clear flags "| FLAGS
```

## Available Admin Flags

Three admin flags are available, each with specific effects on the generator's data state.

### RESET_DB

**Storage key:** `ResetDb`
**Value type:** `Boolean`
**Storage location:** `s3://{bucket}/admin-flags/ResetDb`

**Effect:**

- Deletes the entire RocksDB database (`veglenker.db/`)
- Deletes the S3 backup file (`rocksdb-backup.tar.gz`)
- Clears all admin flags (including itself)
- Forces complete re-initialization on next startup

**When to use:**

- Severe database corruption
- Complete system reset required
- Testing fresh installation scenarios

**Warning:** Most destructive option. All data will be lost and must be re-downloaded from NVDB.

### RESET_ROADNET

**Storage key:** `ResetRoadnet`
**Value type:** `Boolean`
**Storage location:** `s3://{bucket}/admin-flags/ResetRoadnet`

**Effect:**

- Clears the `VEGLENKER` column family in RocksDB
- Removes veglenkesekvens-related settings from key-value store
- Preserves all vegobjekter (road objects) data
- Triggers road network backfill on next cycle

**When to use:**

- Road network data corruption
- Force re-sync of road network only
- Road geometry issues requiring fresh data

**Note:** This flag persists until explicitly deleted or until a successful backup is created.

### RESET_FEATURE_TYPES

**Storage key:** `ResetFeatureTypes`
**Value type:** `Set<Int>` (vegobjekt type IDs)
**Storage location:** `s3://{bucket}/admin-flags/ResetFeatureTypes`

**Effect:**

- Clears specific vegobjekt types from the `VEGOBJEKTER` column family
- Removes type-specific settings for each type
- Preserves road network and other vegobjekt types
- Triggers backfill for specified types on next cycle

**When to use:**

- Specific feature type corruption (e.g., speed limits)
- Force re-sync of specific vegobjekt types
- Data quality issues in particular feature types

**Example types:**

- `105` - Speed limits (Fartsgrense)
- `616` - Lane sections (Feltstrekning)
- `821` - Functional road class (Funksjonell vegklasse)

**Note:** This flag persists until explicitly deleted or until a successful backup is created.

## Setting Admin Flags

### API Endpoint

```
POST /api/v1/admin/flags
```

### Query Parameters

| Parameter             | Type        | Description                            |
|-----------------------|-------------|----------------------------------------|
| `resetDb`             | `Boolean?`  | Set to `true` to reset entire database |
| `resetRoadnet`        | `Boolean?`  | Set to `true` to reset road network    |
| `resetFeatureTypeIds` | `Set<Int>?` | Set of vegobjekt type IDs to reset     |

**Note:** Setting a parameter to `false` (or empty set) will **delete** that flag.

### Examples

#### Reset Entire Database

```bash
curl -X POST "http://localhost:8999/api/v1/admin/flags?resetDb=true"
```

#### Reset Road Network Only

```bash
curl -X POST "http://localhost:8999/api/v1/admin/flags?resetRoadnet=true"
```

#### Clear Specific Feature Types

```bash
# Reset speed limits (105) and lane sections (616)
curl -X POST "http://localhost:8999/api/v1/admin/flags?resetFeatureTypeIds=105,616"
```

#### Clear a Flag

```bash
# Remove the RESET_ROADNET flag
curl -X POST "http://localhost:8999/api/v1/admin/flags?resetRoadnet=false"
```

## Processing Admin Flags

### Startup Processing

The generator processes admin flags during startup before beginning normal operations.

### Processing Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant Restore as RocksDbS3BackupService
    participant AdminFlags as SharedKeyValueStore (S3)
    participant RocksDB as RocksDB Context
    participant Repos as Repositories
    participant S3 as S3/MinIO
    App ->> Restore: restoreIfNeeded()
    Note over Restore: Step 1: Check RESET_DB
    Restore ->> AdminFlags: getValue(RESET_DB)
    AdminFlags -->> Restore: true or false

    alt RESET_DB is true
        Restore ->> RocksDB: clear() - Delete entire DB
        Restore ->> S3: Delete backup file
        Restore ->> AdminFlags: clear() - Delete ALL flags
        Note over Restore: All flags cleared, continues...
    end

    Note over Restore: Step 2: Check if restore needed
    Restore ->> RocksDB: Check if DB exists and has data

    alt DB is empty
        Restore ->> S3: Try to download backup.tar.gz
        alt Backup exists
            Restore ->> RocksDB: Restore from backup
        else Backup missing (e.g., after RESET_DB)
            Note over Restore: Will proceed with full backfill
        end
    end

    Note over Restore: Step 3: Check RESET_ROADNET
    Restore ->> AdminFlags: getValue(RESET_ROADNET)
    AdminFlags -->> Restore: true or false

    alt RESET_ROADNET is true
        Restore ->> RocksDB: clearColumnFamily(VEGLENKER)
        Restore ->> Repos: clearVeglenkesekvensSettings()
    end

    Note over Restore: Step 4: Check RESET_FEATURE_TYPES
    Restore ->> AdminFlags: getValue(RESET_FEATURE_TYPES)
    AdminFlags -->> Restore: Set<Int> or empty

    loop For each type ID
        Restore ->> Repos: clearVegobjektType(typeId)
        Restore ->> Repos: clearVegobjektSettings(typeId)
    end

    Restore -->> App: Processing complete
    Note over App: Normal cycle continues
    App ->> App: Run backfill/update/export
    Note over App: On successful backup
    App ->> Restore: createBackup()
    Restore ->> S3: Upload backup.tar.gz
    Restore ->> AdminFlags: clear() - Delete all flags
```

### Processing Order

1. **Check RESET_DB**
    - If true: Delete database, delete backup, clear **all** flags
    - Execution continues to next step

2. **Check if restore needed**
    - If database is empty/missing: Try to restore from S3 backup
    - If backup missing (e.g., deleted by RESET_DB): Proceed with full backfill
    - If database exists with data: Skip restore

3. **Check RESET_ROADNET**
    - If true: Clear `VEGLENKER` column family and related settings
    - (Note: Will be false if RESET_DB was processed, since it cleared all flags)

4. **Check RESET_FEATURE_TYPES**
    - For each type ID: Clear vegobjekter of that type and related settings
    - (Note: Will be empty if RESET_DB was processed, since it cleared all flags)

5. **Continue normal cycle**
    - Backfill runs for any cleared/missing data
    - Updates process as normal
    - Export generates TN-ITS files

### When Flags Are Cleared

**Automatic clearing:**

- `RESET_DB` - Cleared immediately when RESET_DB flag is processed
- `RESET_ROADNET` and `RESET_FEATURE_TYPES` - Cleared after successful backup creation

**Manual clearing:**

- Any flag can be cleared by setting it to `false` or `null` via the API

## Flag Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: Admin sets flag via API
    Created --> Stored: Flag saved to S3 as JSON
    Stored --> Pending: Generator not yet started
    Pending --> Read: Generator starts
    Read --> Processing: restoreIfNeeded() called

    state Processing {
        [*] --> CheckResetDb
        CheckResetDb --> DeleteDb: RESET_DB = true
        CheckResetDb --> CheckRestore: RESET_DB not set
        DeleteDb --> ClearFlags: Delete DB + backup
        ClearFlags --> CheckRestore: Clear ALL flags, continue
        CheckRestore --> RestoreDb: DB empty
        CheckRestore --> CheckRoadnet: DB exists
        RestoreDb --> CheckRoadnet
        CheckRoadnet --> ClearRoadnet: RESET_ROADNET = true
        CheckRoadnet --> CheckFeatures: RESET_ROADNET not set
        ClearRoadnet --> CheckFeatures
        CheckFeatures --> ClearFeatures: RESET_FEATURE_TYPES has values
        CheckFeatures --> Complete: No types to clear
        ClearFeatures --> Complete
        Complete --> [*]
    }

    Processing --> Active: Flags persist
    Active --> BackupCreated: Successful backup
    BackupCreated --> AutoCleared: clear() called
    AutoCleared --> [*]
    Stored --> ManualCleared: Admin sets flag to false
    Pending --> ManualCleared
    Active --> ManualCleared
    ManualCleared --> [*]
```

## Storage Format

### S3 Object Structure

Flags are stored as individual JSON objects in S3:

```
s3://{bucket}/
  └── admin-flags/
      ├── ResetDb             # Boolean (true)
      ├── ResetRoadnet        # Boolean (true)
      └── ResetFeatureTypes   # JSON array [105, 616, 821]
```

### Example JSON Content

**ResetDb:**

```json
true
```

**ResetRoadnet:**

```json
true
```

**ResetFeatureTypes:**

```json
[
    105,
    616,
    821
]
```

## Configuration

### S3/MinIO Configuration

Both tnits-katalog and tnits-generator must share the same S3 bucket configuration.

**Required environment variables:**

```bash
export S3_ENDPOINT=http://localhost:9000
export S3_ACCESS_KEY=user
export S3_SECRET_KEY=password
export S3_BUCKET=nvdb-tnits-local-data-01
```

These can also be configured via `application.conf` files in each service.

## Security

### OAuth2 Authentication

The admin flags endpoint requires OAuth2 authentication with the `nvdbapi=admin` scope.

The endpoint is protected by Spring Security and requires a valid OAuth2 JWT token with the appropriate scope claim.

### Local Development

For local development, use the mock OAuth server provided via Docker Compose:

**Start mock server:**

```bash
docker compose up -d
```

The mock OAuth server runs on `http://localhost:8099/Employees` and provides test tokens with the required `nvdbapi=admin` scope.

**Run katalog with security enabled:**

```bash
./gradlew tnits-katalog:bootRun --args='--spring.profiles.active=local,local-security'
```

## Related Documentation

- [Architecture Overview](ARCHITECTURE.md) - System architecture
- [Storage Architecture](STORAGE.md) - RocksDB details and operations
- [Getting Started](GETTING_STARTED.md) - Setup and first run
- [Data Flow](DATA_FLOW.md) - How data flows through the system
