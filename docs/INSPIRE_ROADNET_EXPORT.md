# INSPIRE RoadLink Export

This document describes the INSPIRE RoadLink export functionality for the complete Norwegian road network.

## What is INSPIRE?

**INSPIRE** (Infrastructure for Spatial Information in the European Union) is a directive establishing a European spatial data infrastructure for sharing environmental and geographical data across borders.

The **Transport Networks - Road (tn-ro)** theme defines how road network data should be structured and exchanged.

**Purpose:**

- Standardize geographic road network data across Europe
- Enable interoperability between national road databases
- Support cross-border infrastructure planning and analysis
- Provide baseline data for navigation and transport applications

**Specification:** [INSPIRE Transport Networks Technical Guidelines](https://knowledge-base.inspire.ec.europa.eu/publications/inspire-data-specification-transport-networks-technical-guidelines_en)

## What is RoadLink?

**RoadLink** is the core INSPIRE feature representing a linear section of the road network between two points (nodes).

**Key characteristics:**

- Represents the centerline geometry of a road segment
- Includes functional classification (road importance)
- Describes the physical form (motorway, roundabout, etc.)
- Uses standard European coordinate reference systems

## Export Overview

The NVDB INSPIRE export generates a complete snapshot of the active Norwegian road network in INSPIRE v5.0 format.

### Data Source

- **Input:** All veglenker from CachedVegnett (in-memory cache)
- **Filtering:** Only veglenker where `startdato <= today AND (sluttdato IS NULL OR sluttdato > today)`
- **Format:** WFS 2.0 FeatureCollection with RoadLink features
- **Performance:** Uses cached data - no additional RocksDB queries needed

### RoadLink ID Format

Each RoadLink uses a composite ID combining the veglenkesekvens ID and veglenke number:

```
{veglenkesekvensId}-{veglenkenummer}
```

**Examples:**

- `12345-1` → Veglenkesekvens 12345, veglenke 1
- `987654-3` → Veglenkesekvens 987654, veglenke 3

## Coordinate System

### Storage vs Export

| Stage               | Coordinate System               | Notes                                         |
|---------------------|---------------------------------|-----------------------------------------------|
| **NVDB API**        | UTM33 (EPSG:25833) or EPSG:5973 | Source data from NVDB                         |
| **RocksDB Storage** | UTM33 (EPSG:25833)              | Stored in original CRS for precision          |
| **INSPIRE Export**  | UTM33 (EPSG:25833)              | No conversion needed - direct export          |

### Transformation Pipeline

```
NVDB (UTM33) → RocksDB (UTM33) → INSPIRE Export (UTM33)
```

The application uses **ETRS89** datum (EPSG:25833) for INSPIRE exports, which is the Norwegian standard projected coordinate system.

**Coordinate format in XML:**

```xml

<gml:posList>598123.45 6643567.89 598234.56 6643678.90</gml:posList>
```

- Space-separated pairs: `easting northing easting northing ...`
- Precision: 2 decimal places (centimeter accuracy)

## INSPIRE Properties

### Required Properties

#### inspireId

Unique identifier following INSPIRE Identifier pattern:

```xml

<net:inspireId>
  <base:Identifier>
    <base:localId>12345-1</base:localId>
    <base:namespace>http://data.geonorge.no/inspire/tn-ro</base:namespace>
  </base:Identifier>
</net:inspireId>
```

#### centrelineGeometry

LineString geometry representing the road centerline:

```xml

<net:centrelineGeometry>
  <gml:LineString gml:id="RoadLink.12345-1_NET_CENTRELINEGEOMETRY"
                  srsName="urn:ogc:def:crs:EPSG::25833">
    <gml:posList>598123.45 6643567.89 598234.56 6643678.90</gml:posList>
  </gml:LineString>
</net:centrelineGeometry>
```

### Optional Properties

#### functionalRoadClass

Importance/hierarchy of the road in the network:

| NVDB FRC | INSPIRE Value    | Description               |
|----------|------------------|---------------------------|
| FRC_0    | mainRoad         | Main/national roads       |
| FRC_1    | firstClassRoad   | First class roads         |
| FRC_2    | secondClassRoad  | Second class roads        |
| FRC_3    | thirdClassRoad   | Third class roads         |
| FRC_4    | fourthClassRoad  | Fourth class roads        |
| FRC_5    | fifthClassRoad   | Fifth class roads         |
| FRC_6    | sixthClassRoad   | Sixth class roads         |
| FRC_7    | seventhClassRoad | Seventh class/local roads |

**Source:** Derived from NVDB vegobjekttype 826 (Funksjonell vegklasse)

```xml

<tn-ro:functionalRoadClass>thirdClassRoad</tn-ro:functionalRoadClass>
```

#### formOfWay

Physical characteristics of the road:

| NVDB TypeVeg      | INSPIRE Value     | Description             |
|-------------------|-------------------|-------------------------|
| KANALISERT_VEG    | dualCarriageway   | Separated carriageways  |
| ENKEL_BILVEG      | singleCarriageway | Single carriageway road |
| RAMPE             | slipRoad          | On/off ramps            |
| RUNDKJORING       | roundabout        | Roundabout              |
| GATETUN           | trafficSquare     | Traffic square/plaza    |
| GANG_OG_SYKKELVEG | singleCarriageway | Pedestrian/cycle paths  |
| Other types       | singleCarriageway | Default mapping         |

**Source:** Directly from NVDB veglenke `typeVeg` field

```xml

<tn-ro:formOfWay>singleCarriageway</tn-ro:formOfWay>
```

## XML Structure

### Namespaces (INSPIRE v5.0)

```xml

<wfs:FeatureCollection
  xmlns:wfs="http://www.opengis.net/wfs/2.0"
  xmlns:gml="http://www.opengis.net/gml/3.2"
  xmlns:tn-ro="http://inspire.ec.europa.eu/schemas/tn-ro/5.0"
  xmlns:net="http://inspire.ec.europa.eu/schemas/net/5.0"
  xmlns:base="http://inspire.ec.europa.eu/schemas/base/5.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
```

**Important:** Namespaces are declared **once** on the root element, not repeated on child elements.

### Complete Example

```xml

<wfs:FeatureCollection timeStamp="2025-10-07T12:00:00Z" numberMatched="unknown" numberReturned="0">
  <wfs:member>
    <tn-ro:RoadLink gml:id="RoadLink.1896321-1">
      <net:inspireId>
        <base:Identifier>
          <base:localId>1896321-1</base:localId>
          <base:namespace>http://data.geonorge.no/inspire/tn-ro</base:namespace>
        </base:Identifier>
      </net:inspireId>
      <net:centrelineGeometry>
        <gml:LineString gml:id="RoadLink.1896321-1_NET_CENTRELINEGEOMETRY"
                        srsName="urn:ogc:def:crs:EPSG::25833">
          <gml:posList>598123.45 6643567.89 598234.56 6643678.90 598345.67 6643789.01</gml:posList>
        </gml:LineString>
      </net:centrelineGeometry>
      <tn-ro:functionalRoadClass>thirdClassRoad</tn-ro:functionalRoadClass>
      <tn-ro:formOfWay>singleCarriageway</tn-ro:formOfWay>
    </tn-ro:RoadLink>
  </wfs:member>
</wfs:FeatureCollection>
```

## Usage

### Command Line

```bash
# Export complete INSPIRE RoadLink dataset
./gradlew run --args="inspire-road-net"
```

### Process Flow

1. **Restore** - Restore RocksDB backup from S3 (if needed)
2. **Backfill** - Ensure all veglenkesekvenser are loaded
3. **Update** - Process any pending change events
4. **Backup** - Create RocksDB backup (pre-export)
5. **Initialize Cache** - Load CachedVegnett with FRC data
6. **Export** - Generate and upload INSPIRE XML
7. **Backup** - Create final RocksDB backup

### Export Targets

#### File Export (Development)

**Configuration:**

```hocon
exporter {
  target = File
  gzip = true
}
```

**Output:**

```
/tmp/INSPIRE_RoadNet_2025-10-07T12-00-00Z.xml.gz
```

#### S3 Export (Production)

**Configuration:**

```hocon
exporter {
  target = S3
  bucket = "nvdb-tnits"
  gzip = true
}
```

**Output:**

```
s3://nvdb-tnits/inspire-roadnet/2025-10-07T12-00-00Z/roadlinks.xml.gz
```

## Performance

### Export Characteristics

| Metric                       | Typical Value            |
|------------------------------|--------------------------|
| **Total RoadLinks**          | ~500,000 - 1,000,000     |
| **Processing Rate**          | ~10,000 RoadLinks/second |
| **Export Time**              | 1-5 minutes              |
| **File Size (compressed)**   | ~50-100 MB               |
| **File Size (uncompressed)** | ~500-1000 MB             |

### Optimization Techniques

1. **In-Memory Cache** - Uses CachedVegnett (already loaded for TN-ITS) - no additional RocksDB reads
2. **Streaming Processing** - Uses Kotlin Flow to process veglenker without loading all into memory
3. **Lazy Transformation** - Converts coordinates only as needed during XML writing
4. **Buffered I/O** - 256KB buffers for file and S3 writes
5. **GZIP Compression** - Optional compression reduces file size by ~90%

## Data Quality

### Active Veglenker Filter

Only veglenker that are currently valid are exported:

```kotlin
startdato <= today && (sluttdato == null || sluttdato > today)
```

This ensures:

- No future road segments (not yet built)
- No expired road segments (demolished/changed)
- Only currently active parts of the road network

### Functional Road Class Availability

**FRC data availability:**

- FRC is loaded from NVDB vegobjekttype 826 during CachedVegnett initialization
- Only available for "relevant" veglenker (used in speed limit calculations)
- Missing FRC results in property being omitted from XML (not an error)

**Relevant veglenker criteria:**

- Top-level detail level (VEGTRASE or VEGTRASE_OG_KJOREBANE)
- Has lane overview (feltoversikt)
- Is active (valid date range)
- Specific road types (not ferries, footpaths, stairs, etc.)

## Integration with TN-ITS

The INSPIRE export complements the existing TN-ITS export:

| Feature          | TN-ITS                          | INSPIRE RoadLink      |
|------------------|---------------------------------|-----------------------|
| **Purpose**      | Traffic regulations             | Road network geometry |
| **Scope**        | Speed limits (+ other features) | All road segments     |
| **Updates**      | Incremental (snapshot + deltas) | Full snapshot only    |
| **Coordinates**  | WGS84 (EPSG:4326)               | UTM33 (EPSG:25833)    |
| **Format**       | Custom TN-ITS XML               | INSPIRE/WFS XML       |
| **Typical Size** | 10-50 MB                        | 50-100 MB             |
| **Storage**      | UTM33, converted to WGS84       | UTM33, direct export  |

Both exports use the same underlying RocksDB storage and CachedVegnett infrastructure.

## Troubleshooting

### Missing FRC Data

**Symptom:** Some RoadLinks missing `functionalRoadClass` property

**Cause:** FRC data not available in NVDB for that veglenke

**Solution:** This is expected - FRC is optional and may not exist for all road types

### Coordinate Precision Issues

**Symptom:** Coordinates don't match expected UTM33 values

**Cause:** Transformation or rounding errors

**Check:**

```kotlin
// Verify transformation
val utm33Geom = wgs84Geom.projectTo(SRID.UTM33)
// Should be in range:
// Easting: ~-100,000 to 1,100,000
// Northing: 6,400,000 to 7,950,000 (Norway)
```

### Export Hangs or Times Out

**Symptom:** Export process doesn't complete

**Possible causes:**

1. RocksDB not initialized (missing backfill)
2. CachedVegnett initialization failed
3. Memory issues with large dataset

**Debug:**

```bash
./gradlew run --args="inspire-road-net" --debug
```

## References

- [INSPIRE Transport Networks Specification](https://inspire.ec.europa.eu/id/document/tg/tn)
- [INSPIRE RoadLink Feature Concept](https://inspire.ec.europa.eu/featureconcept/RoadLink)
- [EPSG:25833 (ETRS89 / UTM zone 33N)](https://epsg.io/25833)
- [NVDB API Documentation](https://nvdbapiles.atlas.vegvesen.no)
- [WFS 2.0 Specification](http://www.opengeospatial.org/standards/wfs)
