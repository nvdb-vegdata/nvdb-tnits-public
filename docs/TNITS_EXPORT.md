# TN-ITS Export

This document describes the TN-ITS (Transport Network - Intelligent Transport Systems) export functionality.

## What is TN-ITS?

**TN-ITS** is a European specification for exchanging road network and traffic regulation data between countries. It's part of the INSPIRE directive for spatial data infrastructure in the European Union.

**Purpose:**

- Standardize road network data exchange
- Enable cross-border intelligent transport systems
- Support automated driving and navigation

**Specification:** [spec.tn-its.eu](https://spec.tn-its.eu)

## Export Types

The application generates two types of exports:

### Snapshot Export

**Purpose:** Complete dataset at a point in time

**Characteristics:**

- Contains ALL active objects
- Generated monthly
- Larger file size (~50-200 MB compressed)

**Use case:** Initial data load, complete refresh

**Command:**

```bash
./gradlew run --args="snapshot"
```

**Output location:**

```
s3://bucket/0105-speedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
```

### Update Export

**Purpose:** Only changed data since last snapshot/update

**Characteristics:**

- Contains only DIRTY objects
- Generated daily (configurable)
- Smaller file size (~1-10 MB compressed)

**Use case:** Incremental synchronization

**Command:**

```bash
./gradlew run --args="update"
```

**Output location:**

```
s3://bucket/0105-speedLimit/2025-10-06T14-30-00Z/update.xml.gz
```

## Export Process

### High-Level Flow

```
1. Load road network into memory (CachedVegnett)
2. Determine scope (all or dirty)
3. Fetch vegobjekter from RocksDB
4. Process in parallel workers
5. Generate TN-ITS features
6. Stream to XML output
7. Upload to S3/MinIO
8. Clear dirty state (for updates)
```

**Implementation:**
- `core/useCases/PerformSmartTnitsExport.kt` - Orchestrates the overall export decision
- `core/services/tnits/TnitsExportService.kt` - Core export orchestration
- `core/services/tnits/FeatureTransformer.kt` - Feature generation (formerly TnitsFeatureGenerator)
- `infrastructure/s3/TnitsFeatureS3Exporter.kt` - S3 upload

### Detailed Steps

#### Step 1: Initialize Cached Road Network

```kotlin
cachedVegnett.initialize()
```

**What happens:**

- Load all veglenkesekvenser from RocksDB
- Build in-memory lookup structures
- Load supporting vegobjekter (FRC, directions)

**Memory usage:** ~2-3 GB

**Time:** ~30-60 seconds

See: `core/services/vegnett/CachedVegnett.kt`

#### Step 2: Determine Export Scope

**Snapshot:**

```kotlin
val allIds = vegobjekterRepository.getAllIds(typeId = 105)
```

**Update:**

```kotlin
val dirtyIds = dirtyCheckingRepository.getAllDirtyVegobjektIds(typeId = 105)
val dirtyByVegnett = dirtyCheckingRepository.findVegobjekterOnDirtyVeglenkesekvenser(typeId = 105)
val scope = dirtyIds + dirtyByVegnett
```

#### Step 3: Parallel Processing

Uses worker pool for performance:

```kotlin
val workers = Runtime.getRuntime().availableProcessors()
val batches = allIds.chunked(1000)

val results = batches.parallelStream()
  .flatMap { batch ->
    batch.map { id ->
      tnitsFeatureGenerator.generateFeature(typeId, id, timestamp)
    }.stream()
  }
  .sorted(compareBy { it.identifier })
  .collect(Collectors.toList())
```

**Worker count:** Matches CPU cores (typically 4-16)

**Batch size:** 100-1000 objects per batch

#### Step 4: Generate TN-ITS Features

For each vegobjekt:

```kotlin
fun generateFeature(typeId: Int, objektId: Long, timestamp: Instant): TnitsFeature {
  // 1. Fetch vegobjekt
  val vegobjekt = vegobjekterRepository.get(typeId, objektId)

  // 2. Extract stedfesting (positioning)
  val stedfestinger = vegobjekt.stedfesting.linjer

  // 3. Calculate geometry
  val geometry = calculateGeometry(stedfestinger)
    .projectTo(SRID.WGS84)
    .simplify(distanceTolerance = 1.0)

  // 4. Encode OpenLR
  val openLrRefs = openLrService.toOpenLr(stedfestinger)

  // 5. Extract properties
  val speedLimitValue = extractSpeedLimit(vegobjekt)
  val validFrom = vegobjekt.metadata.startdato
  val validTo = vegobjekt.metadata.sluttdato

  // 6. Build TN-ITS feature
  return TnitsFeature(
    identifier = "NO-105-$objektId",
    geometry = geometry,
    openLrLocationReferences = openLrRefs,
    speedLimitValue = speedLimitValue,
    validFrom = validFrom,
    validTo = validTo,
    updateType = determineUpdateType(vegobjekt)
  )
}
```

See: `core/services/tnits/FeatureTransformer.kt`

#### Step 5: Stream XML Output

Uses StAX for memory-efficient streaming:

```kotlin
writeXmlDocument(outputStream, "tn-its:SpeedLimitDataset", namespaces) {
  features.forEach { feature ->
    "tn-its:speedLimit" {
      "tn-its:identifier" {
        +feature.identifier
      }
      "tn-its:geometry" {
        writeGeometry(feature.geometry)
      }
      "tn-its:openLRLocationReference" {
        +feature.openLrRefs.first().base64
      }
      "tn-its:speedLimitValue" {
        attribute("uom", "km/h")
        +feature.speedLimitValue.toString()
      }
      "tn-its:validFrom" {
        +feature.validFrom.toString()
      }
      if (feature.validTo != null) {
        "tn-its:validTo" {
          +feature.validTo.toString()
        }
      }
    }
  }
}
```

See: `core/presentation/XmlStreamDsl.kt`

#### Step 6: Upload to S3

**Direct streaming to S3:**

```kotlin
val s3Key = generateS3Key(typeId, timestamp, exportType)
val outputStream = S3OutputStream(minioClient, bucket, s3Key)

if (gzipEnabled) {
  GZIPOutputStream(outputStream).use { gzipStream ->
    writeXml(gzipStream)
  }
} else {
  outputStream.use { writeXml(it) }
}
```

**S3 key format:**

```
{typeId-padded}-{typeName}/{timestamp}/{type}.xml[.gz]
```

**Example:**

```
0105-speedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
```

See: `infrastructure/s3/S3OutputStream.kt`

## TN-ITS Data Mapping

### Speed Limit Mapping

NVDB → TN-ITS field mapping:

| NVDB Field                             | TN-ITS Field                    | Notes                       |
|----------------------------------------|---------------------------------|-----------------------------|
| `metadata.startdato` (first version)   | `validFrom`                     | Inception date              |
| `metadata.startdato` (current version) | `beginLifespanVersion`          | Current version start       |
| `metadata.sluttdato`                   | `validTo`, `endLifespanVersion` | For closed/deleted objects  |
| `egenskaper[2021]`                     | `speedLimitValue`               | Speed limit in km/h         |
| `stedfesting` → geometry               | `geometry`                      | WGS84 coordinates           |
| `stedfesting` → OpenLR                 | `openLRLocationReference`       | Encoded location            |
| Vegobjekt type + ID                    | `identifier`                    | Format: `NO-105-{objektId}` |

### Update Type Rules

| Vegobjekt State                     | Update Type | validTo Behavior        |
|-------------------------------------|-------------|-------------------------|
| Active, not in previous export      | `Modify`    | null                    |
| Active, in previous export, changed | `Modify`    | null                    |
| Closed                              | `Modify`    | Set to sluttdato        |
| Deleted                             | `Remove`    | Set to export timestamp |

**Implementation:** `core/model/tnits/UpdateType.kt`, `core/services/tnits/FeatureTransformer.kt`

### Business Rules

From `README.md:12`:

**validFrom:**

- Set to start date of object's first version

**validTo / endLifespanVersion:**

- For closed objects: Set to end date of object's last version
- For deleted objects: Set to export timestamp

**beginLifespanVersion:**

- Set to start date of object's current version

**FRC (Functional Road Class):**

- Read from vegobjekt 821 (Funksjonell vegklasse)
- Fallback to FRC 7 if not found
- If multiple on same link, use lowest priority (highest FRC value)

**Direction:**

- Read from veglenk's field overview
- For connection links: from vegobjekt 616 (Feltstrekning)

## XML Structure

### Namespaces

```xml

<tn-its:SpeedLimitDataset
  xmlns:tn-its="http://inspire.ec.europa.eu/schemas/tn-its/4.0"
  xmlns:gml="http://www.opengis.net/gml/3.2"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://inspire.ec.europa.eu/schemas/tn-its/4.0
                        https://inspire.ec.europa.eu/schemas/tn-its/4.0/TrafficRegulationOrder.xsd">
```

### Feature Structure

```xml

<tn-its:member>
  <tn-its:SpeedLimit gml:id="NO-105-85283590">
    <tn-its:identifier>NO-105-85283590</tn-its:identifier>
    <tn-its:updateType>Baseline</tn-its:updateType>

    <tn-its:validFrom>2020-01-15T00:00:00Z</tn-its:validFrom>
    <tn-its:beginLifespanVersion>2020-01-15T00:00:00Z</tn-its:beginLifespanVersion>

    <tn-its:geometry>
      <gml:LineString gml:id="geom-NO-105-85283590" srsName="EPSG:4326">
        <gml:posList>
          10.12345 59.98765
          10.12346 59.98766
          10.12347 59.98767
        </gml:posList>
      </gml:LineString>
    </tn-its:geometry>

    <tn-its:openLRLocationReference>
      CwRbWyNG9RpsCQCb/jsbtAT+Bv8=
    </tn-its:openLRLocationReference>

    <tn-its:speedLimitSource>
      <tn-its:SpeedLimitSourceValue>measurementDevice</tn-its:SpeedLimitSourceValue>
    </tn-its:speedLimitSource>

    <tn-its:speedLimitValue uom="km/h">80</tn-its:speedLimitValue>
  </tn-its:SpeedLimit>
</tn-its:member>
```

## OpenLR Encoding

**OpenLR** (Open Location Referencing) is a standard for encoding road locations independently of specific map versions.

**Purpose:**

- Reference locations without requiring same map version
- Enable location decoding on different map databases
- Support cross-border location references

**Specification:** [openlr.org](https://www.openlr.org)

### Encoding Process

```
Stedfesting → Veglenker → OpenLR Lines → Encode → Base64
```

**Implementation:** `core/services/vegnett/OpenLrService.kt`

#### Step 1: Build OpenLR Lines

For each veglenk:

```kotlin
val line = OpenLrLine(
  id = veglenk.id,
  startNode = OpenLrNode(
    geometry = veglenk.geometri.startPoint,
    frc = getFrc(veglenk)
  ),
  endNode = OpenLrNode(
    geometry = veglenk.geometri.endPoint,
    frc = getFrc(veglenk)
  ),
  frc = getFrc(veglenk),
  fow = FormOfWay.SINGLE_CARRIAGEWAY,
  length = veglenk.lengde
)
```

#### Step 2: Calculate Offsets

```kotlin
val positiveOffset = findOffsetInMeters(
  veglenk = firstVeglenk,
  posisjon = stedfesting.startposisjon,
  isStart = true
)

val negativeOffset = findOffsetInMeters(
  veglenk = lastVeglenk,
  posisjon = stedfesting.sluttposisjon,
  isStart = false
)
```

#### Step 3: Create Path

```kotlin
val path = pathFactory.create(
  lines = openLrLines,
  positiveOffset = positiveOffset,
  negativeOffset = negativeOffset
)
```

#### Step 4: Encode

```kotlin
val location = locationFactory.createLineLocation(path)
val encoded = encoder.encode(location)
val base64 = Base64.getEncoder().encodeToString(encoded.data)
```

**Note:** Current implementation uses placeholder encoding. Production requires full OpenLR library integration.

## Geometry Handling

### Coordinate Systems

| System    | EPSG Code | Usage                      |
|-----------|-----------|----------------------------|
| **UTM33** | 25833     | NVDB storage, calculations |
| **WGS84** | 4326      | TN-ITS output, OpenLR      |

### Transformation Pipeline

```
NVDB Geometry (UTM33)
    ↓
Extract segment based on stedfesting
    ↓
Merge connected segments
    ↓
Simplify (Douglas-Peucker, 1m tolerance)
    ↓
Project to WGS84
    ↓
TN-ITS XML output
```

**Implementation:** `core/extensions/GeometryHelpers.kt`

### Geometry Simplification

**Algorithm:** Douglas-Peucker

**Tolerance:** 1.0 meter (configurable)

**Purpose:**

- Reduce file size
- Remove unnecessary vertices
- Preserve visual appearance

```kotlin
val simplified = geometry.simplify(distanceTolerance = 1.0)
```

**Example:**

- Original: 1000 points
- Simplified: 50-100 points
- Size reduction: ~90%

## Hash-Based Change Detection

The export tracks content hashes to detect actual changes vs. metadata-only changes.

### Hash Calculation

```kotlin
val hash = SipHasher.hash(
  typeId.toByteArray() +
    objektId.toByteArray() +
    speedLimit.toByteArray() +
    geometry.toByteArray() +
    validFrom.toByteArray() +
    validTo?.toByteArray()
)
```

See: `core/services/hash/SipHasher.kt`

### Change Detection

```kotlin
val previousHash = vegobjekterHashStore.get(typeId, objektId)
val currentHash = calculateHash(vegobjekt)

if (previousHash != currentHash) {
  // Content changed, include in export
  export(vegobjekt)
  vegobjekterHashStore.put(typeId, objektId, currentHash)
} else {
  // No content change, skip
}
```

**Benefit:** Avoid exporting objects where only metadata changed (e.g., internal IDs)

## S3 Export Configuration

### File Organization

```
bucket/
└── {typeId-padded}-{typeName}/
    ├── 2025-10-06T10-00-00Z/
    │   ├── snapshot.xml.gz
    │   └── timestamp.txt
    ├── 2025-10-06T14-00-00Z/
    │   ├── update.xml.gz
    │   └── timestamp.txt
    └── 2025-10-07T10-00-00Z/
        ├── snapshot.xml.gz
        └── timestamp.txt
```

### Type ID Formatting

Speed limits use vegobjekttype 105:

```
0105-speedLimit/
```

**Padding:** 4 digits, zero-padded

### Timestamp Format

ISO 8601 with colons replaced by hyphens:

```
2025-10-06T12-00-00Z
```

**Reason:** Some filesystems don't support colons in filenames

### Configuration

```hocon
exporter {
  gzip = true                           # Enable GZIP compression
  target = S3                           # S3 or File
  bucket = nvdb-tnits-local-data-01    # S3 bucket name
}

s3 {
  endpoint = "http://localhost:9000"
  accessKey = user
  secretKey = password
}
```

See: `config/AppConfig.kt:12`

## Performance Optimization

### Parallel Processing

**Workers:** CPU core count

```kotlin
val workers = Runtime.getRuntime().availableProcessors()
```

**Typical speedup:** 4-8x on modern CPUs

### Streaming Output

**Memory usage:**

- With streaming: O(1) - constant
- Without streaming: O(n) - entire file

**File sizes:**

- Snapshot: 50-200 MB (compressed)
- Update: 1-10 MB (compressed)

### GZIP Compression

**Compression ratio:** ~10:1

- Uncompressed: 500 MB
- Compressed: 50 MB

**CPU overhead:** Minimal (~5-10% slower)

## Automatic Mode

The application can run in automatic mode, deciding whether to generate snapshots or updates based on timestamps.

**Logic:**

```kotlin
val hasSnapshotThisMonth = lastSnapshot?.isInCurrentMonth() ?: false
val hasUpdateToday = lastUpdate?.isToday() ?: false

if (!hasSnapshotThisMonth) {
  generateSnapshot()
} else if (!hasUpdateToday) {
  generateUpdate()
}
```

**Configuration:**

- Snapshot: Once per month
- Update: Once per day

See: `core/useCases/PerformSmartTnitsExport.kt`

## Validation

### XSD Validation

The generated XML can be validated against TN-ITS XSD schemas.

**Test:** `XsdValidationTest.kt`

```kotlin
val schemaUrl = "https://inspire.ec.europa.eu/schemas/tn-its/4.0/TrafficRegulationOrder.xsd"
val validator = createValidator(schemaUrl)
validator.validate(xmlSource)
```

### Manual Validation

```bash
xmllint --schema https://inspire.ec.europa.eu/schemas/tn-its/4.0/TrafficRegulationOrder.xsd \
    snapshot.xml
```

## Troubleshooting

### Empty Exports

**Problem:** Export contains no features

**Causes:**

- No dirty items (for updates)
- No active objects (for snapshots)
- Filtering removed all items

**Solution:** Check dirty state, verify data in RocksDB

### Invalid Geometry

**Problem:** Geometry validation errors in XML

**Causes:**

- Empty geometries after simplification
- Invalid coordinate order
- Self-intersecting linestrings

**Solution:** Adjust simplification tolerance, check source data

### S3 Upload Failures

**Problem:** Failed to upload to S3

**Causes:**

- MinIO not running
- Invalid credentials
- Network issues

**Solution:** Check Docker, verify configuration, test connectivity

### Out of Memory

**Problem:** OOM during export

**Causes:**

- CachedVegnett too large
- Too many objects in memory

**Solution:** Increase heap size, reduce batch size, use streaming more

## Related Documentation

- [Architecture Overview](ARCHITECTURE.md) - System design
- [Data Flow](DATA_FLOW.md) - How data flows to export
- [Storage Architecture](STORAGE.md) - RocksDB storage
- [Concepts Glossary](CONCEPTS.md) - TN-ITS and NVDB terms
