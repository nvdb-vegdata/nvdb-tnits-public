# Data Flow

This document describes how data flows through the nvdb-tnits system, from NVDB APIs to TN-ITS exports.

## Overview

The system operates in three main phases:

```
NVDB APIs → Backfill → Incremental Updates → TN-ITS Export → S3/MinIO
```

Each phase has specific responsibilities and data transformations.

## Phase 1: Initial Backfill

The backfill phase downloads all road network data from NVDB when starting from an empty database.

### Backfill Flow Diagram

```
┌──────────────────┐
│  NVDB Uberiket   │
│      API         │
└──────────────────┘
         │
         │ 1. Fetch all veglenkesekvenser
         │    (paginated, ~1.2M records)
         ▼
┌─────────────────────────────┐
│ VeglenkesekvensService      │
│ processVeglenkesekvenser()  │
└─────────────────────────────┘
         │
         │ 2. Store in RocksDB
         ▼
┌──────────────────┐
│ VEGLENKER        │
│ column family    │
└──────────────────┘
         │
         │ 3. Fetch all vegobjekter
         │    (paginated by type)
         ▼
┌───────────────────────┐
│ VegobjekterService    │
│ processVegobjekter()  │
└───────────────────────┘
         │
         │ 4. Store in RocksDB
         ▼
┌──────────────────┐
│ VEGOBJEKTER      │
│ column family    │
└──────────────────┘
```

### Backfill Process Details

**Implementation:** `handlers/PerformBackfillHandler.kt`

#### Step 1: Veglenkesekvenser Backfill

```kotlin
// Pseudocode
fun performVeglenkesekvensBackfill() {
  if (isBackfillComplete()) return

  var lastId = getLastProcessedVeglenkesekvensId() ?: 0

  while (true) {
    val batch = uberiketApi.fetchVeglenkesekvenser(
      start = lastId,
      limit = 1000
    )

    if (batch.isEmpty()) break

    veglenkerStore.batchInsert(batch)
    lastId = batch.last().id
    keyValueStore.setLastProcessedVeglenkesekvensId(lastId)
  }

  keyValueStore.setBackfillComplete(true)
}
```

**API Endpoint:** `GET /vegnett/veglenkesekvenser`

**Data retrieved:**

- Veglenkesekvens ID
- List of veglenker (road links)
- Geometry (linestrings)
- Position data (start/stop along parent sequence)
- Field information (lanes, direction)

#### Step 2: Vegobjekter Backfill

```kotlin
// Pseudocode
fun performVegobjekterBackfill(typeId: Int) {
  if (isBackfillComplete(typeId)) return

  var lastId = getLastProcessedVegobjektId(typeId) ?: 0

  while (true) {
    val batch = uberiketApi.fetchVegobjekter(
      typeId = typeId,
      start = lastId,
      limit = 1000,
      includeAll = true  // Geometry, properties, positioning
    )

    if (batch.isEmpty()) break

    vegobjekterStore.batchInsert(typeId, batch)
    lastId = batch.last().id
    keyValueStore.setLastProcessedVegobjektId(typeId, lastId)
  }

  keyValueStore.setBackfillComplete(typeId, true)
}
```

**API Endpoint:** `GET /vegobjekter/{typeId}`

**Data retrieved:**

- Vegobjekt ID and type
- Properties (e.g., speed limit value)
- Stedfesting (positioning on road network)
- Metadata (dates, versions)

### Backfill Performance

**Typical timings:**

- Veglenkesekvenser: ~20-30 minutes (~1.2M records)
- Speed limits (type 105): ~10-15 minutes (~250K records)
- Supporting types: ~5-10 minutes

**Optimization:**

- Batch inserts (1000 records per batch)
- Progress tracking (resumable on failure)
- Parallel processing (not currently implemented)

## Phase 2: Incremental Updates

After backfill completes, the system processes change events from NVDB to keep data current.

### Update Flow Diagram

```
┌──────────────────┐
│  NVDB Uberiket   │
│  Events API      │
└──────────────────┘
         │
         │ 1. Fetch change events since last update
         │    (veglenkesekvens hendelser)
         ▼
┌──────────────────┐
│ VeglenkesekvensService
│ processHendelser()
└──────────────────┘
         │
         │ 2. Process each event
         ├─────────┬──────────┬──────────┐
         │         │          │          │
         ▼         ▼          ▼          ▼
    ┌────────┐ ┌──────┐  ┌──────┐  ┌────────┐
    │ CREATED│ │UPDATE│  │CLOSED│  │DELETED │
    └────────┘ └──────┘  └──────┘  └────────┘
         │         │          │          │
         └─────────┴──────────┴──────────┘
                     │
                     │ 3. Update RocksDB & mark dirty
                     ▼
         ┌───────────────────────────┐
         │ VEGLENKER + DIRTY_*       │
         └───────────────────────────┘
                     │
                     │ 4. Fetch vegobjekt events
                     ▼
┌──────────────────┐
│ VegobjekterService
│ processHendelser()
└──────────────────┘
         │
         │ 5. Update RocksDB & mark dirty
         ▼
┌──────────────────┐
│ VEGOBJEKTER +    │
│ DIRTY_*          │
└──────────────────┘
```

### Update Process Details

**Implementation:** `handlers/PerformUpdateHandler.kt`

#### Event Types

| Event Type  | Action                  | RocksDB Operation   |
|-------------|-------------------------|---------------------|
| **CREATED** | New road segment/object | Insert + Mark dirty |
| **UPDATED** | Modified data           | Update + Mark dirty |
| **CLOSED**  | End-dated but kept      | Update + Mark dirty |
| **DELETED** | Removed completely      | Delete + Mark dirty |

#### Veglenkesekvens Events

```kotlin
// Pseudocode
fun processVeglenkesekvensEvents() {
  val lastEventId = keyValueStore.getLastProcessedEventId() ?: 0

  val events = uberiketApi.fetchVeglenkesekvensHendelser(
    since = lastEventId
  )

  rocksDbContext.writeBatch {
    events.forEach { event ->
      when (event.type) {
        CREATED, UPDATED -> {
          val data = uberiketApi.fetchVeglenkesekvens(event.id)
          veglenkerStore.upsert(event.id, data)
        }
        DELETED -> {
          veglenkerStore.delete(event.id)
        }
      }

      dirtyCheckingStore.markVeglenkesekvensAsDirty(event.id)
    }

    keyValueStore.setLastProcessedEventId(events.last().id)
  }
}
```

**API Endpoint:** `GET /vegnett/veglenkesekvenser/hendelser`

#### Vegobjekt Events

```kotlin
// Pseudocode
fun processVegobjektEvents(typeId: Int) {
  val lastEventId = keyValueStore.getLastProcessedEventId(typeId) ?: 0

  val events = uberiketApi.fetchVegobjektHendelser(
    typeId = typeId,
    since = lastEventId
  )

  rocksDbContext.writeBatch {
    events.forEach { event ->
      when (event.type) {
        CREATED, UPDATED, CLOSED -> {
          val data = uberiketApi.fetchVegobjekt(typeId, event.id)
          vegobjekterStore.upsert(typeId, event.id, data)
        }
        DELETED -> {
          // Keep for export with removed status
          val data = vegobjekterStore.get(typeId, event.id)
          if (data != null) {
            vegobjekterStore.upsert(typeId, event.id, data.markAsDeleted())
          }
        }
      }

      dirtyCheckingStore.markVegobjektAsDirty(typeId, event.id)
    }

    keyValueStore.setLastProcessedEventId(typeId, events.last().id)
  }
}
```

**API Endpoint:** `GET /vegobjekter/{typeId}/hendelser`

### Update Performance

**Typical timings:**

- 0-100 events: ~1-2 seconds
- 100-1000 events: ~10-30 seconds
- 1000+ events: ~1-5 minutes

## Phase 3: TN-ITS Export

The export phase generates TN-ITS XML files from RocksDB data.

### Export Flow Diagram

```
┌──────────────────┐
│  Export Command  │
│  (snapshot/update)
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ CachedVegnett    │
│ initialize()     │
│ Load all road    │
│ network into RAM │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ Determine scope  │
│ • Snapshot: ALL  │
│ • Update: DIRTY  │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ TnitsFeatureGenerator
│ generateFeature()│
│ • Fetch vegobjekt│
│ • Calculate      │
│   geometry       │
│ • Encode OpenLR  │
│ • Transform WGS84│
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ TnitsFeatureExporter
│ exportSnapshot() │
│ or exportUpdate()│
│ • Parallel       │
│   processing     │
│ • Stream XML     │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ XmlStreamDsl     │
│ Generate XML     │
│ (StAX streaming) │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ S3OutputStream   │
│ or FileOutput    │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ S3/MinIO Storage │
│ 0105-speedLimit/ │
│ timestamp/       │
│ snapshot.xml.gz  │
└──────────────────┘
```

### Export Types

#### Snapshot Export

**Purpose:** Full export of all current data

**Process:**

1. Load all vegobjekter of type
2. Generate TN-ITS features for each
3. Set `updateType = Baseline`
4. Include all active objects

**Output example:**

```
s3://bucket/0105-speedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
```

#### Update Export

**Purpose:** Delta export of only changed data since last snapshot/update

**Process:**

1. Query dirty vegobjekter
2. Query vegobjekter on dirty veglenkesekvenser
3. Generate TN-ITS features for dirty items
4. Set `updateType = Modify` or `Remove`
5. Include only changed objects

**Output example:**

```
s3://bucket/0105-speedLimit/2025-10-06T14-30-00Z/update.xml.gz
```

### Parallel Processing

The export uses parallel processing for performance:

```
┌─────────────────────────────────────────────┐
│  Orchestrator Thread                        │
│  • Fetch batches of IDs (steplock)          │
│  • Distribute to workers                    │
└─────────────────────────────────────────────┘
         │
         ├───────────┬───────────┬───────────┐
         ▼           ▼           ▼           ▼
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │Worker 1│  │Worker 2│  │Worker 3│  │Worker N│
    │Process │  │Process │  │Process │  │Process │
    │IDs     │  │IDs     │  │IDs     │  │IDs     │
    │1-1000  │  │1001-   │  │2001-   │  │N*1000- │
    │        │  │2000    │  │3000    │  │...     │
    └────────┘  └────────┘  └────────┘  └────────┘
         │           │           │           │
         └───────────┴───────────┴───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │ Sort by ID (ordered)  │
         └───────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │ Stream to XML output  │
         └───────────────────────┘
```

**Worker count:** `Runtime.getRuntime().availableProcessors()`

### Data Transformations

#### 1. Stedfesting to Geometry

**Input:** Stedfesting (positioning on road network)

```json
{
  "linjer": [
    {
      "id": 41251,
      "startposisjon": 0.0,
      "sluttposisjon": 0.5,
      "retning": "MED"
    }
  ]
}
```

**Process:**

1. Fetch veglenkesekvens geometry from cache
2. Calculate intersection with stedfesting range
3. Extract geometry segment

**Output:** JTS LineString in UTM33 projection

See: `geometry/GeometryHelpers.kt:78`

#### 2. OpenLR Encoding

**Input:** List of veglenker with geometry

**Process:**

1. Build OpenLR Line objects
2. Find FRC (Functional Road Class) from vegklasse
3. Determine bearing at nodes
4. Calculate offsets
5. Encode to binary OpenLR

**Output:** Base64-encoded OpenLR string

See: `openlr/OpenLrService.kt:46`

**Note:** Current implementation uses placeholder encoding. Real OpenLR encoding requires an OpenLR library.

#### 3. Coordinate Transformation

**Input:** Geometry in UTM33 (EPSG:25833)

**Process:**

1. Project to WGS84 (EPSG:4326)
2. Use GeoTools CRS transformation
3. Longitude/latitude order for OpenLR

**Output:** WGS84 coordinates

```kotlin
geometry.projectTo(SRID.WGS84)
```

See: `geometry/GeometryHelpers.kt:46`

#### 4. Geometry Simplification

**Input:** High-resolution linestring

**Process:**

1. Apply Douglas-Peucker algorithm
2. Tolerance: configurable (e.g., 1.0 meter)
3. Preserve topology

**Output:** Simplified linestring

```kotlin
geometry.simplify(distanceTolerance = 1.0)
```

See: `geometry/GeometryHelpers.kt:66`

## Data Dependencies

### Required Vegobjekt Types

| Type ID | Name                  | Purpose                        |
|---------|-----------------------|--------------------------------|
| **105** | Fartsgrense           | Speed limits (main export)     |
| **821** | Funksjonell vegklasse | FRC for OpenLR                 |
| **616** | Feltstrekning         | Direction for connection links |

Defined in: `model/VegobjektTyper.kt:5`

### Type Configuration

```kotlin
val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(
  VegobjektTyper.FELTSTREKNING,
  VegobjektTyper.FUNKSJONELL_VEGKLASSE
)
```

See: `Application.kt:16`

## State Tracking

### Progress Tracking Keys

Stored in `KEY_VALUE` column family:

```kotlin
// Backfill progress
"backfill_complete_veglenkesekvenser" -> Boolean
"backfill_complete_105" -> Boolean  // Speed limits

// Event processing
"last_processed_event_id_veglenkesekvenser" -> Long
"last_processed_event_id_105" -> Long

// Export timestamps
"last_snapshot_105" -> Instant
"last_update_105" -> Instant
"last_update_check_105" -> Instant
```

### Dirty State Tracking

```
DIRTY_VEGLENKESEKVENSER: Set<Long>
DIRTY_VEGOBJEKTER_105: Set<(typeId, objektId)>
```

After successful export:

```kotlin
dirtyCheckingStore.clearDirtyVegobjekter(typeId = 105)
```

## Error Handling

### Retry Logic

NVDB API calls use exponential backoff retry:

```kotlin
HttpRequestRetry {
  retryOnServerErrors(maxRetries = 5)
  retryOnException(maxRetries = 5, retryOnTimeout = true)
  exponentialDelay(base = 2.0, maxDelayMs = 30_000)
}
```

See: `Services.kt:60`

### Transaction Rollback

RocksDB batch operations are atomic:

```kotlin
rocksDbContext.writeBatch {
  // All succeed or all fail
  veglenkerStore.batchUpdate(updates)
  dirtyCheckingStore.markDirty(ids)
}
```

If any operation fails, entire batch is rolled back.

### Progress Persistence

Progress is persisted after each batch, enabling resumable operations:

```kotlin
keyValueStore.setLastProcessedEventId(lastId)
// On restart, continues from lastId
```

## Performance Optimization

### Caching Strategy

**CachedVegnett:** Load entire road network into RAM

**Benefit:**

- Fast lookups during export (no RocksDB access)
- Enable complex spatial queries

**Memory usage:** ~2-3 GB for Norwegian road network

See: `vegnett/CachedVegnett.kt`

### Batch Sizing

| Operation         | Batch Size | Reason                     |
|-------------------|------------|----------------------------|
| Backfill fetch    | 1000       | API limit                  |
| RocksDB write     | 1000-10000 | Balance memory/performance |
| Export processing | 100-1000   | Worker pool efficiency     |

### Streaming XML

Use StAX (Streaming API for XML) instead of DOM:

**Memory usage:**

- DOM: O(n) - entire document in memory
- StAX: O(1) - constant memory

See: `xml/XmlStreamDsl.kt`

## Monitoring Progress

During execution, the application logs progress:

```
INFO  Backfill: processed 10000/1234567 veglenkesekvenser (0.8%)
INFO  Backfill: processed 50000/1234567 veglenkesekvenser (4.0%)
INFO  Backfill complete: 1234567 veglenkesekvenser
INFO  Update: processed 123 events
INFO  Export: processing 45678 speed limits
INFO  Export: completed in 4m 32s
```

## Related Documentation

- [Architecture Overview](ARCHITECTURE.md) - System design
- [Storage Architecture](STORAGE.md) - RocksDB details
- [TN-ITS Export](TNITS_EXPORT.md) - Export specifics
- [Concepts Glossary](CONCEPTS.md) - Domain terminology
