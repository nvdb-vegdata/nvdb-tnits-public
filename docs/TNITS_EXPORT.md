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
s3://bucket/0105-SpeedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
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
s3://bucket/0105-SpeedLimit/2025-10-06T14-30-00Z/update.xml.gz
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

Uses worker pool for performance, processing vegobjekter in parallel batches.

**Worker count:** Matches CPU cores (typically 4-16)

**Batch size:** 1000 objects per batch

#### Step 4: Generate TN-ITS Features

For each vegobjekt:

1. Fetch vegobjekt from RocksDB
2. Extract stedfesting (positioning)
3. Calculate geometry from veglenker (stored in UTM33, simplified to 1m tolerance)
4. Encode OpenLR location references
5. Extract feature-specific properties (e.g., speed limit value, road name)
6. Build TnitsFeature with all location references

**Note:** Conversion to WGS84 happens during XML writing.

See: `core/services/tnits/FeatureTransformer.kt`

#### Step 5: Stream XML Output

Uses StAX for memory-efficient streaming. Features are written as a flow to avoid loading all features into memory at once.

**Key characteristics:**

- Streaming output (constant memory usage)
- Generic `RoadFeature` structure for all feature types
- Properties stored as key-value pairs with codelist references
- Multiple location reference formats (geometry, OpenLR, NVDB-specific)

See: `core/presentation/TnitsXmlWriter.kt` and `core/presentation/XmlStreamDsl.kt`

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
0105-SpeedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
```

See: `infrastructure/s3/S3OutputStream.kt`

## TN-ITS Data Mapping

### Speed Limit Mapping

NVDB → XML field mapping:

| NVDB Field                             | XML Path                                                    | Notes                               |
|----------------------------------------|-------------------------------------------------------------|-------------------------------------|
| `metadata.startdato` (first version)   | `RoadFeature/validFrom`                                     | Inception date                      |
| `metadata.startdato` (current version) | `RoadFeature/beginLifespanVersion`                          | Current version start (as instant)  |
| `metadata.sluttdato`                   | `RoadFeature/validTo`, `RoadFeature/endLifespanVersion`     | For closed/deleted objects          |
| `egenskaper[2021]`                     | `RoadFeature/properties/GenericRoadFeatureProperty/value`   | Speed limit in km/h                 |
| `stedfesting` → geometry               | `RoadFeature/locationReference/GeometryLocationReference`   | Stored in UTM33, output as WGS84    |
| `stedfesting` → OpenLR                 | `RoadFeature/locationReference/OpenLRLocationReference`     | Base64-encoded binary location ref  |
| `stedfesting` → NVDB format            | `RoadFeature/locationReference/LocationByExternalReference` | NVDB-specific positioning reference |
| Vegobjekt ID                           | `RoadFeature/id/RoadFeatureId/id`                           | NVDB vegobjekt ID                   |
| Type 105                               | `RoadFeature/type@xlink:href`                               | Codelist reference: `#speedLimit`   |

### Update Type Rules

| Vegobjekt State              | Update Type | validTo Behavior        |
|------------------------------|-------------|-------------------------|
| New (not in previous export) | `Add`       | null                    |
| Modified                     | `Modify`    | null                    |
| Closed (has sluttdato)       | `Modify`    | Set to sluttdato        |
| Deleted                      | `Remove`    | Set to export timestamp |

**Note:** In snapshots, all features use update type `Snapshot` and the `updateInfo` element is omitted.

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

- Read from veglenke's field overview
- For connection links: from vegobjekt 616 (Feltstrekning)

## XML Structure

### Namespaces

```xml

<RoadFeatureDataset
    xmlns="http://spec.tn-its.eu/schemas/"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:gml="http://www.opengis.net/gml/3.2"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://spec.tn-its.eu/schemas/ http://spec.tn-its.eu/schemas/TNITS.xsd
                      http://www.opengis.net/gml/3.2 http://schemas.opengis.net/gml/3.2.1/gml.xsd">
```

### Dataset Structure

```xml

<RoadFeatureDataset>
    <metadata>
        <Metadata>
            <datasetId>NVDB-TNITS-105-SpeedLimit_Snapshot_2025-09-26T10:30:00Z</datasetId>
            <datasetCreationTime>2025-09-26T10:30:00Z</datasetCreationTime>
        </Metadata>
    </metadata>
    <type>Snapshot</type>

    <!-- Features follow... -->
    <roadFeatures>
        <RoadFeature>
            <!-- See Feature Structure below -->
        </RoadFeature>
    </roadFeatures>
</RoadFeatureDataset>
```

### Feature Structure

Each feature (speed limit, road name, etc.) uses a generic `RoadFeature` element with type-specific properties:

```xml

<roadFeatures>
    <RoadFeature>
        <validFrom>2009-01-01</validFrom>
        <beginLifespanVersion>2008-12-31T23:00:00Z</beginLifespanVersion>

        <!-- For updates only -->
        <updateInfo>
            <UpdateInfo>
                <type>Add</type> <!-- Add, Modify, or Remove -->
            </UpdateInfo>
        </updateInfo>

        <source xlink:href="http://spec.tn-its.eu/codelists/RoadFeatureSourceCode#regulation"/>
        <type xlink:href="http://spec.tn-its.eu/codelists/RoadFeatureTypeCode#speedLimit"/>

        <properties>
            <GenericRoadFeatureProperty>
                <type xlink:href="http://spec.tn-its.eu/codelists/RoadFeaturePropertyType#maximumSpeedLimit"/>
                <value>50</value>
            </GenericRoadFeatureProperty>
        </properties>

        <id>
            <RoadFeatureId>
                <providerId>nvdb.no</providerId>
                <id>85283803</id>
            </RoadFeatureId>
        </id>

        <locationReference>
            <GeometryLocationReference>
                <encodedGeometry>
                    <gml:LineString srsDimension="2" srsName="EPSG:4326">
                        <gml:posList>63.43004 10.45458 63.42982 10.45447 ...</gml:posList>
                    </gml:LineString>
                </encodedGeometry>
            </GeometryLocationReference>
        </locationReference>

        <locationReference>
            <OpenLRLocationReference>
                <binaryLocationReference>
                    <BinaryLocationReference>
                        <base64String>CwdvMy0bFzLQBwIZ/u8zOy0=</base64String>
                        <openLRBinaryVersion xlink:href="http://spec.tn-its.eu/codelists/OpenLRBinaryVersionCode#v2_4"/>
                    </BinaryLocationReference>
                </binaryLocationReference>
            </OpenLRLocationReference>
        </locationReference>

        <locationReference>
            <LocationByExternalReference>
                <predefinedLocationReference xlink:href="nvdb.no:0-0.4010989@41423:MED:-:-"/>
            </LocationByExternalReference>
        </locationReference>
    </RoadFeature>
</roadFeatures>
```

## OpenLR Encoding

**OpenLR** (Open Location Referencing) is a standard for encoding road locations independently of specific map versions.

**Purpose:**

- Reference locations without requiring same map version
- Enable location decoding on different map databases
- Support cross-border location references

**Specification:** [openlr.org](https://www.openlr.org)

### Encoding Process

OpenLR encoding converts NVDB positioning (stedfesting) to binary OpenLR format:

1. **Build OpenLR Lines** - Convert veglenker to OpenLR line format with FRC and FOW
2. **Calculate Offsets** - Determine start/end offsets within the first/last veglenke
3. **Create Path** - Build OpenLR path from lines and offsets
4. **Encode** - Encode to binary format and Base64

**Output format:** Base64-encoded binary OpenLR location reference (version 2.4)

**Implementation:** `core/services/vegnett/OpenLrService.kt`

## Geometry Handling

### Coordinate Systems

| System    | EPSG Code | Usage                              |
|-----------|-----------|------------------------------------|
| **UTM33** | 25833     | NVDB source, RocksDB storage       |
| **WGS84** | 4326      | TN-ITS XML output, OpenLR encoding |

### Transformation Pipeline

**Storage (done by sync process):**

```
NVDB full veglenke geometries (UTM33)
    ↓
Store complete veglenke geometries in RocksDB (UTM33)
```

**Export (done during TN-ITS export):**

```
Vegobjekt stedfesting (e.g., positions 0.3-0.7 on veglenkesekvens 123)
    ↓
Fetch full veglenke geometries from RocksDB
    ↓
Extract segments based on stedfesting positions (using LengthIndexedLine)
    ↓
Merge extracted segments (if vegobjekt spans multiple veglenker)
    ↓
Simplify (Douglas-Peucker, 1m tolerance)
    ↓
Project to WGS84
    ↓
TN-ITS XML output
```

**Key point:** Full veglenke geometries are stored in RocksDB. Segment extraction and merging happen at export time based on each vegobjekt's stedfesting.

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

The export tracks content hashes of TnitsFeature objects to detect actual changes vs. metadata-only changes.

Each TnitsFeature is serialized to binary format (ProtoBuf) and hashed using SipHash. When processing updates, the current hash is compared with the previously exported hash to determine if the feature has truly changed.

**Benefit:** Avoid exporting objects where only NVDB internal metadata changed without affecting the TN-ITS output.

**Implementation:** `core/services/hash/SipHasher.kt`, `core/model/tnits/TnitsFeature.kt`

## S3 Export Configuration

### File Organization

```
bucket/
└── {typeId-padded}-{typeName}/
    ├── 2025-10-06T10-00-00Z/
    │   └── snapshot.xml.gz
    ├── 2025-10-06T14-00-00Z/
    │   └── update.xml.gz
    └── 2025-10-07T10-00-00Z/
        └── snapshot.xml.gz
```

### Type ID Formatting

Speed limits use vegobjekttype 105:

```
0105-SpeedLimit/
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

if (!hasUpdateToday) {
    generateUpdate()
}
if (!hasSnapshotThisMonth) {
    generateSnapshot()
}
```

**Configuration:**

- Snapshot: Once per month
- Update: Once per day

See: `core/useCases/TnitsAutomaticCycle.kt`

## Validation

### XSD Validation

The generated XML can be validated against the TN-ITS XSD schema referenced in the output:

```
http://spec.tn-its.eu/schemas/TNITS.xsd
```

### Manual Validation

Use `xmllint` or similar XML validation tools to check schema compliance:

```bash
xmllint --noout snapshot.xml
```

For viewing the output structure, see the test resources in `src/test/resources/` for example exports.

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
