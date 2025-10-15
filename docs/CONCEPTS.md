# Concepts Glossary

This document defines key concepts and terminology used in the nvdb-tnits project, covering both NVDB-specific and TN-ITS/geospatial terms.

## NVDB Concepts

### Veglenkesekvens

**Norwegian:** Road link sequence

**Definition:** A sequence of connected road links (veglenker) that represents a logical road segment. Used as the primary unit for organizing the road network.

**Example:**

- ID: 41423
- Contains: 5 veglenker
- Length: 2.5 km
- Represents: Highway E6 from junction A to junction B

**In code:** `core/model/Veglenke.kt`, `infrastructure/rocksdb/VeglenkerRocksDbStore.kt`

### Veglenke

**Norwegian:** Road link

**Definition:** A single segment of road between two nodes (intersections or endpoints). The atomic unit of the road network.

**Properties:**

- `id`: Unique identifier
- `veglenkesekvensId`: Parent sequence
- `startposisjon`: Start position (0.0-1.0) within parent sequence
- `sluttposisjon`: End position (0.0-1.0) within parent sequence
- `lengde`: Length in meters
- `geometri`: LineString geometry (UTM33)
- `felt`: Lane/field information

**Example:**

```kotlin
Veglenke(
    id = 1234567,
    veglenkesekvensId = 41423,
    startposisjon = 0.2,
    sluttposisjon = 0.4,
    lengde = 500.0,  // meters
    geometri = LineString()
)
```

### Vegobjekt

**Norwegian:** Road object

**Definition:** Features or attributes associated with the road network, such as speed limits, road signs, bridges, etc.

**Types (examples):**

- **105:** Fartsgrense (Speed limit)
- **821:** Funksjonell vegklasse (Functional road class)
- **616:** Feltstrekning (Lane section)

**Properties:**

- `id`: Object ID
- `type`: Vegobjekt type
- `metadata`: Version, dates, etc.
- `egenskaper`: Properties (type-specific values)
- `stedfesting`: Positioning on road network

**Example:**

```kotlin
Vegobjekt(
    id = 85283590,
    type = VegobjektType(id = 105, navn = "Fartsgrense"),
    egenskaper = [
        Egenskap(id = 2021, verdi = 80)  // Speed: 80 km/h
    ],
    stedfesting = Stedfesting()
)
```

See: `core/model/Vegobjekt.kt`, `tnits-common/.../VegobjektTyper.kt`

### Stedfesting

**Norwegian:** Positioning, location

**Definition:** Describes how a vegobjekt is positioned on the road network.

**Structure:**

```kotlin
Stedfesting(
    linjer = [
        Linje(
            id = 41423,                    // Veglenkesekvens ID
            startposisjon = 0.25,          // Start position (0.0-1.0)
            sluttposisjon = 0.75,          // End position (0.0-1.0)
            retning = Retning.MED          // Direction
        )
    ]
)
```

**Interpretation:**

- Object is positioned on veglenkesekvens 41423
- From 25% to 75% along the sequence
- In the direction of the road (MED = with, MOT = against)

**In code:** `core/model/StedfestingUtstrekning.kt`

### Retning

**Norwegian:** Direction

**Values:**

- **MED:** With the road direction (forward)
- **MOT:** Against the road direction (backward)

**Usage:** Determines which direction a speed limit or other regulation applies.

### Egenskap

**Norwegian:** Property

**Definition:** A typed property value of a vegobjekt.

**Example:**

```kotlin
Egenskap(
    id = 2021,              // Property type: "Fartsgrense"
    verdi = 80,             // Value: 80 km/h
    datatype = "INTEGER"
)
```

**Common egenskapstyper:**

- **2021:** Fartsgrense (Speed limit value)
- **9338:** Vegklasse (Road class)
- **5528:** Feltoversikt i veglenkeretning (Lane overview)

See: `tnits-common/.../VegobjektTyper.kt` and `core/model/EgenskapsTyper.kt`

### Feltoversikt

**Norwegian:** Lane overview

**Definition:** Description of lanes on a road segment, including count and allowed directions.

**Example:**

```
"#1#2" = 2 lanes total, lane 1 allows traffic in veglenke direction
```

**Usage:** Determine allowed driving directions for OpenLR encoding.

### Hendelser

**Norwegian:** Events

**Definition:** Change events in NVDB that track modifications to road network or objects.

**Event types:**

- **CREATED:** New object/link added
- **UPDATED:** Existing object/link modified
- **CLOSED:** Object/link end-dated but preserved
- **DELETED:** Object/link permanently removed

**Usage:** Enable incremental updates by processing only changed data.

**API endpoint:** `/vegnett/veglenkesekvenser/hendelser`

## TN-ITS Concepts

### TN-ITS

**Full name:** Transport Network - Intelligent Transport Systems

**Definition:** European specification for exchanging road network and traffic regulation data between countries, part of the INSPIRE directive.

**Purpose:**

- Standardize road data exchange
- Support cross-border ITS
- Enable automated driving

**Specification:** [spec.tn-its.eu](https://spec.tn-its.eu)

### Update Type

**Definition:** Indicates the type of change in a TN-ITS export.

**Values:**

- **Add:** Object created
- **Modify:** Object updated
- **Remove:** Object deleted or no longer applicable

**Usage in exports:**

- Snapshot: Update type is not specified
- Update: Objects have `updateType` as `Add`, `Modify`, or `Remove`

See: `core/model/tnits/UpdateType.kt`

### Lifespan Versioning

TN-ITS tracks object lifecycle with multiple date fields:

**validFrom:**

- When the object became valid in reality
- Set to start date of object's first version

**validTo:**

- When the object ceased to be valid
- Set for closed/deleted objects

**beginLifespanVersion:**

- When this version of the object was created
- Set to start date of current version

**endLifespanVersion:**

- When this version was superseded
- Set to validTo for closed/deleted objects

**Example timeline:**

```
2020-01-01: Speed limit created (50 km/h)
  validFrom = 2020-01-01
  beginLifespanVersion = 2020-01-01

2022-06-15: Speed limit changed to 60 km/h
  validFrom = 2020-01-01 (unchanged)
  beginLifespanVersion = 2022-06-15 (new version)

2024-12-31: Speed limit removed
  validFrom = 2020-01-01
  validTo = 2024-12-31
  beginLifespanVersion = 2022-06-15
  endLifespanVersion = 2024-12-31
```

## Geospatial Concepts

### Coordinate Reference System (CRS)

**Definition:** System for defining positions on Earth using coordinates.

**Systems used:**

#### UTM33 (EPSG:25833)

**Full name:** Universal Transverse Mercator zone 33N

**Coverage:** Norway and surrounding areas

**Units:** Meters (X, Y)

**Usage in project:**

- NVDB storage format
- Distance calculations
- Geometry operations

**Example coordinates:**

```
X: 598123.45 meters (easting)
Y: 6643567.89 meters (northing)
```

#### WGS84 (EPSG:4326)

**Full name:** World Geodetic System 1984

**Coverage:** Global

**Units:** Degrees (longitude, latitude)

**Usage in project:**

- TN-ITS export format
- OpenLR encoding

**Coordinate order:**

- GML: Latitude, Longitude (depends on CRS definition)

**Example coordinates:**

```
Latitude: 59.9139 degrees
Longitude: 10.7522 degrees
```

See: `core/extensions/GeometryHelpers.kt`

### Geometry Types

#### LineString

**Definition:** Sequence of connected line segments defined by two or more points.

**Example:**

```
LINESTRING(59.0 10.0, 59.1 10.1, 59.2, 10.2)
```

**Usage:** Represent road geometries.

#### Point

**Definition:** Single coordinate location.

**Example:**

```
POINT(59.9139 10.7522)
```

**Usage:** Node locations, intersections.

#### MultiLineString

**Definition:** Collection of LineString geometries.

**Usage:** Non-connected road segments.

### Well-Known Text (WKT)

**Definition:** Text representation of geometry.

**Examples:**

```
LINESTRING(0 0, 10 0, 10 10)
POINT(5 5)
MULTILINESTRING((0 0, 10 0), (20 0, 30 0))
```

**Library:** JTS (Java Topology Suite)

See: `core/extensions/GeometryHelpers.kt`

### SRID

**Definition:** Spatial Reference System Identifier - numeric code identifying a CRS.

**Common values:**

- **4326:** EPSG 4326: WGS84 (lat/lon)
- **25833:** EPSG 2533: UTM33, 2D only
- **5973:** EPSG 5973: UTM33 with height (3D)

**Usage in JTS:**

```kotlin
geometry.srid = 25833
```

### Geometry Simplification

**Algorithm:** Douglas-Peucker

**Purpose:** Reduce number of points in geometry while preserving shape.

**Tolerance:** Maximum distance a point can deviate from simplified line.

**Example:**

```kotlin
// Original: 1000 points
val simplified = geometry.simplify(distanceTolerance = 1.0)  // 1 meter in UTM33
// Result: ~100 points (90% reduction)
```

See: `core/extensions/GeometryHelpers.kt`

### Coordinate Transformation

**Definition:** Converting coordinates from one CRS to another.

**Library:** GeoTools

**Example:**

```kotlin
val utm33Geometry = parseWkt("LINESTRING(598123 6643567, 598223 6643667)", SRID.UTM33)
val wgs84Geometry = utm33Geometry.projectTo(SRID.WGS84)
// Result: LINESTRING(59.9139 10.7522, 59.9148 10.7537)
```

See: `core/extensions/GeometryHelpers.kt`

## OpenLR Concepts

### OpenLR

**Full name:** Open Location Referencing

**Definition:** Method for encoding locations on road networks in a map-agnostic way.

**Purpose:**

- Reference locations without requiring same map version
- Enable location decoding on different maps
- Support cross-border location exchange

**Specification:** [openlr.org](https://www.openlr.org)

### Location Reference Point (LRP)

**Definition:** Key point along a path used to encode location.

**Properties:**

- Coordinates (longitude, latitude)
- Bearing (direction of travel)
- FRC (Functional Road Class)
- FOW (Form of Way)
- Distance to next LRP

**Encoding:** Binary format, then Base64 for transport.

### Functional Road Class (FRC)

**Definition:** Classification of road importance.

**Values:**

- **FRC 0:** Main road (motorway)
- **FRC 1:** First class road
- **FRC 2:** Second class road
- **FRC 3:** Third class road
- **FRC 4:** Fourth class road
- **FRC 5:** Fifth class road
- **FRC 6:** Sixth class road
- **FRC 7:** Other road (default fallback)

**Source in NVDB:** Vegobjekt 821 (Funksjonell vegklasse)

**Mapping:**

```kotlin
fun EnumVerdi.toFrc() = when (verdi) {
    13060 -> FRC_0
    13061 -> FRC_1
    13062 -> FRC_2
    13063 -> FRC_3
    13064 -> FRC_4
    13065 -> FRC_5
    13066 -> FRC_6
    else -> FRC_7
}
```

See: `tnits-common/.../VegobjektTyper.kt` and `core/services/vegnett/OpenLrService.kt`

### Form of Way (FOW)

**Definition:** Physical road characteristics.

**Values:**

- **Motorway**
- **Multiple carriageway**
- **Single carriageway**
- **Roundabout**
- **Traffic square**
- **Sliproad**
- **Other**

### Offset

**Definition:** Distance in meters from start or end of path to actual location.

**Types:**

- **Positive offset:** Distance from start
- **Negative offset:** Distance from end

**Example:**

```
Path: 1000 meters
Positive offset: 100 meters  (start at 100m from beginning)
Negative offset: 50 meters   (end at 50m before end)
Effective range: 100m to 950m (850 meters total)
```

See: `core/services/vegnett/OpenLrService.kt`

## Storage Concepts

### Column Family

**Definition:** Logical grouping of related data in RocksDB, similar to tables in relational databases.

**Purpose:**

- Organize different data types
- Independent compression settings
- Efficient range scans

**In project:**

- VEGLENKER
- VEGOBJEKTER
- DIRTY_VEGLENKESEKVENSER
- DIRTY_VEGOBJEKTER
- KEY_VALUE

See: [Storage Architecture](STORAGE.md)

### Dirty Checking

**Definition:** Tracking which data has changed to enable efficient delta processing.

**Mechanism:**

- Mark items as "dirty" when modified
- Query dirty items for processing
- Clear dirty state after successful processing

**Use case:** Generate TN-ITS update exports with only changed speed limits.

**Implementation:**

- DIRTY_VEGLENKESEKVENSER column family
- DIRTY_VEGOBJEKTER column family

See: `infrastructure/rocksdb/DirtyCheckingRocksDbStore.kt`

### Protocol Buffers (Protobuf)

**Definition:** Binary serialization format for structured data.

**Advantages:**

- Compact binary format
- Fast serialization/deserialization
- Type-safe
- Schema evolution support

**Usage:** Serialize all domain objects for RocksDB storage.

**Example:**

```kotlin
@Serializable
data class Veglenke(
    val id: Long,
    val lengde: Double,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometri: Geometry
)

// Serialize
val bytes = ProtoBuf.encodeToByteArray(Veglenke.serializer(), veglenke)

// Deserialize
val veglenke = ProtoBuf.decodeFromByteArray(Veglenke.serializer(), bytes)
```

See: [Storage Architecture](STORAGE.md)

### Unit of Work

**Design pattern:** Group multiple operations into a single atomic transaction.

**In project:** `writeBatch` context for atomic RocksDB operations.

**Example:**

```kotlin
rocksDbContext.writeBatch {
    veglenkerStore.batchUpdate(updates)
    dirtyCheckingStore.markDirty(ids)
    keyValueStore.setLastProcessedId(lastId)
}
// All operations succeed or all fail
```

See: `core/services/storage/WriteBatchContext.kt`

## Processing Concepts

### Backfill

**Definition:** Initial bulk download of all data from NVDB.

**Process:**

1. Download all veglenkesekvenser
2. Download all vegobjekter (by type)
3. Store in RocksDB
4. Mark backfill complete

**Duration:** 30-60 minutes for full Norwegian road network.

**Resumable:** Tracks progress, can resume after failure.

See: [Data Flow](DATA_FLOW.md), `core/services/nvdb/NvdbBackfillOrchestrator.kt`

### Incremental Update

**Definition:** Processing only changed data since last update using NVDB event stream.

**Process:**

1. Fetch events since last processed event ID
2. Process each event (created/updated/deleted)
3. Update RocksDB
4. Mark affected items as dirty

**Frequency:** Typically run daily.

See: [Data Flow](DATA_FLOW.md), `core/services/nvdb/NvdbUpdateOrchestrator.kt`

### Parallel Processing

**Definition:** Using multiple worker threads to process data concurrently.

**Architecture:**

- Orchestrator fetches ID batches
- Workers process IDs independently
- Results sorted by ID before output

**Workers:** CPU core count (e.g., 8 workers on 8-core CPU)

**Speedup:** 4-8x typical performance improvement

See: `core/services/tnits/TnitsExportService.kt`

### Streaming Processing

**Definition:** Processing data incrementally without loading entire dataset into memory.

**Techniques:**

- StAX for XML generation (vs DOM)
- Kotlin Sequences for lazy evaluation
- Direct streaming to S3 output

**Memory usage:** O(1) constant vs O(n) for in-memory approaches

See: `core/presentation/XmlStreamDsl.kt`

## API Concepts

### Uberiket API

**Definition:** NVDB's comprehensive API for accessing road network and object data.

**Base URL:** `https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/`

**Key endpoints:**

- `/vegnett/veglenkesekvenser` - Road network
- `/vegobjekter/{typeId}` - Road objects
- `/vegnett/veglenkesekvenser/hendelser` - Change events

**Documentation:** https://nvdbapiles.atlas.vegvesen.no/

### Datakatalog API

**Definition:** NVDB's metadata API for vegobjekt type definitions and property schemas.

**Base URL:** `https://nvdbapiles.atlas.vegvesen.no/datakatalog/api/v1/`

**Usage:** Fetch property type metadata (names, descriptions, units).

### Pagination

**Definition:** Splitting large result sets into manageable chunks.

**NVDB pagination:**

- Default page size: 1000
- Use `start` parameter for offset
- Continue until empty result

**Example:**

```kotlin
var start = 0
while (true) {
    val batch = api.fetch(start = start, limit = 1000)
    if (batch.isEmpty()) break
    process(batch)
    start = batch.last().id
}
```

## Miscellaneous Concepts

### Repository Pattern

**Definition:** Abstraction layer between business logic and data storage.

**Purpose:**

- Decouple business logic from storage implementation
- Enable testing with mock repositories
- Centralize data access logic

**Example:**

```kotlin
interface VeglenkerRepository {
    fun get(veglenkesekvensId: Long): List<Veglenke>?
    fun upsert(veglenkesekvensId: Long, veglenker: List<Veglenke>)
    fun batchGet(ids: Collection<Long>): Map<Long, List<Veglenke>>
}

class VeglenkerRocksDbStore(
    private val rocksDbContext: RocksDbContext
) : VeglenkerRepository {
    // Implementation using RocksDB
}
```

### Composition Root

**Definition:** Single location where all dependencies are constructed and wired together.

**In project:** `MainModule.kt` - Koin module with component scanning

**Pattern:**

The project uses **Koin** with annotations for dependency injection. Dependencies are declared using `@Singleton` annotations and automatically discovered through component scanning.

```kotlin
@Module
@Configuration
@ComponentScan
class MainModule {
    @Singleton
    fun appConfig() = loadConfig()

    @Singleton
    @Named("uberiketHttpClient")
    fun uberiketHttpClient(config: UberiketApiConfig) =
        createUberiketHttpClient(config.baseUrl)

    @Singleton
    fun minioClient(config: AppConfig): MinioClient = /* ... */
}
```

Services and repositories are annotated with `@Singleton` for automatic discovery:

```kotlin
@Singleton
class VeglenkerRocksDbStore(
    private val rocksDbContext: RocksDbContext
) : VeglenkerRepository {
    // Automatically binds to VeglenkerRepository interface
}

@Singleton
class PerformSmartTnitsExport(
    private val tnitsExportService: TnitsExportService,
    private val rocksDbBackupService: RocksDbS3BackupService,
    // Dependencies automatically resolved by Koin
) {
    suspend fun execute() { /* ... */
    }
}
```

**Usage:**

```kotlin
fun main(args: Array<String>) = runBlocking {
    startKoin {
        printLogger(Level.INFO)
        modules(MainModule().module)
    }

    val app = koin.get<Application>()
    app.main(args)
}
```

For comprehensive documentation on Koin usage, see [Koin Dependency Injection Guide](KOIN_DEPENDENCY_INJECTION.md).

### Cached Vegnett

**Definition:** In-memory cache of entire road network for fast lookups during export.

**Structure:**

- All veglenkesekvenser
- All veglenker
- Supporting vegobjekter (FRC, directions)

**Memory usage:** ~2-3 GB

**Benefit:** Enable complex spatial queries without RocksDB access.

See: `core/services/vegnett/CachedVegnett.kt`

## Abbreviations

| Abbreviation | Full Term                                         |
|--------------|---------------------------------------------------|
| **NVDB**     | Nasjonal vegdatabank (Norwegian Road Database)    |
| **TN-ITS**   | Transport Network - Intelligent Transport Systems |
| **CRS**      | Coordinate Reference System                       |
| **SRID**     | Spatial Reference System Identifier               |
| **UTM**      | Universal Transverse Mercator                     |
| **WGS**      | World Geodetic System                             |
| **WKT**      | Well-Known Text                                   |
| **OpenLR**   | Open Location Referencing                         |
| **FRC**      | Functional Road Class                             |
| **FOW**      | Form of Way                                       |
| **LRP**      | Location Reference Point                          |
| **JTS**      | Java Topology Suite                               |
| **StAX**     | Streaming API for XML                             |
| **GZIP**     | GNU Zip (compression algorithm)                   |
| **S3**       | Simple Storage Service                            |

## Related Documentation

- [Architecture Overview](ARCHITECTURE.md) - System design
- [Data Flow](DATA_FLOW.md) - How data flows through system
- [Storage Architecture](STORAGE.md) - RocksDB details
- [TN-ITS Export](TNITS_EXPORT.md) - Export functionality
- [Getting Started](GETTING_STARTED.md) - Setup guide
- [Testing Guide](TESTING.md) - Testing conventions
