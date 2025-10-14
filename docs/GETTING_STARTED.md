# Getting Started

This guide will help you set up the nvdb-tnits project and run it for the first time.

You might also want to check out the documentation for [Concepts](CONCEPTS.md) related to NVDB and TN-ITS, as well as the [Architecture Overview](ARCHITECTURE.md) for a high-level understanding of the system.

## Prerequisites

Ensure you have the following installed on your development machine:

| Tool         | Minimum Version | Purpose                    | Download                           |
|--------------|-----------------|----------------------------|------------------------------------|
| **Java JDK** | 25+             | Runtime and compilation    | [Adoptium](https://adoptium.net)   |
| **Docker**   | 20.10+          | Running MinIO for local S3 | [docker.com](https://docker.com)   |
| **Git**      | 2.30+           | Source control             | [git-scm.com](https://git-scm.com) |

**Note:** Gradle wrapper is included in the project, so you don't need to install Gradle separately.

## Initial Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd nvdb-tnits-public
```

### 2. Install Git Hooks

The project uses ktlint for code formatting. Install the pre-commit hook to automatically format code before commits:

```bash
./gradlew installGitHooks
```

This ensures consistent code formatting across the team.

### 3. Start MinIO (Local S3)

MinIO provides S3-compatible object storage for local development:

```bash
docker compose up -d
```

This starts MinIO on port 9000 with:

- **Console UI:** http://localhost:9001
- **API endpoint:** http://localhost:9000
- **Default credentials:** user / password

You can access the MinIO console to view uploaded files and manage buckets.

### 4. Verify Java Version

```bash
java -version
```

Should show Java 25 or higher.

## First Run

### Understanding the CLI Commands

The application has three main commands:

```bash
./gradlew run                      # Auto mode (default)
./gradlew run --args="snapshot"    # Generate full TN-ITS snapshot
./gradlew run --args="update"      # Generate delta update
```

**Auto mode** (default):

- Checks if a snapshot has been taken this month
- Checks if an update has been taken today
- Runs the appropriate operation automatically

See: `core/useCases/PerformSmartTnitsExport.kt` for auto mode logic

### Run the Application

On first run, the application will:

1. Check for RocksDB backup in S3 (won't find any)
2. Perform initial backfill from NVDB (downloads all road network data)
3. Process incremental updates
4. Generate a TN-ITS snapshot
5. Upload to MinIO/S3
6. Create a RocksDB backup

**Start the generator:**

```bash
./gradlew tnits-generator:run
```

**Expected output:**

```
INFO  Starting NVDB TN-ITS application on process 12345
INFO  No RocksDB backup found in S3, skipping restore
INFO  Starting backfill of veglenkesekvenser...
INFO  Backfill complete: 1234567 veglenkesekvenser processed
INFO  Starting backfill of vegobjekter...
INFO  Backfill complete: 234567 speed limits processed
INFO  Generating TN-ITS snapshot...
INFO  Snapshot exported to S3: 0105-speedLimit/2025-10-06T12-00-00Z/snapshot.xml.gz
INFO  NVDB TN-ITS application finished
```

**First run timing:**

- Initial backfill: 30-60 minutes (depends on network and NVDB API performance)
- Snapshot generation: 5-10 minutes
- Total: ~45-75 minutes

**Note:** The backfill downloads all Norwegian road network data, which is substantial. Subsequent runs will be much faster using incremental updates.

### Verify the Export

Check MinIO console at http://localhost:9001 (login: user / password)

You should see:

- Bucket: `nvdb-tnits-local-data-01`
- Folder: `0105-speedLimit/YYYY-MM-DDTHH-MM-SSZ/`
- Files: `snapshot.xml.gz` and `update.xml.gz`

### Run the Katalog Service (Optional)

The katalog service provides HTTP access to exported files:

```bash
./gradlew tnits-katalog:bootRun
```

Access the API at http://localhost:8999:

- `GET /api/v1/snapshots/{typeId}/latest` - List latest snapshot
- `GET /api/v1/updates/{typeId}?from=<timestamp>` - List available updates since a given time
- `GET /api/v1/download?path={file}` - Download a specific file

## Common Development Tasks

### Run Tests

```bash
# Run all tests
./gradlew test

# Run tests in a specific module
./gradlew tnits-generator:test

# Run a specific test class
./gradlew test --tests='XmlStreamDslTest'

# Run tests with detailed output
./gradlew test --info
```

See [Testing Guide](TESTING.md) for advanced testing patterns.

### Format Code

Code formatting happens automatically via git hooks, but you can manually format:

```bash
# Check formatting
./gradlew ktlintCheck

# Auto-format all code
./gradlew ktlintFormat
```

### Generate API Models

The project uses OpenAPI Generator to create Java models from NVDB API specifications:

```bash
./gradlew generateAllApiModels
```

This generates:

- `src/generated/java/no/vegvesen/nvdb/apiles/uberiket/` - Road network API models
- `src/generated/java/no/vegvesen/nvdb/apiles/datakatalog/` - Metadata API models

**When to regenerate:**

- After NVDB API updates
- When model classes are missing
- Fresh clone of the repository

### Clean Build

```bash
./gradlew clean build
```

This removes all build artifacts and performs a complete rebuild.

### View RocksDB Data (Advanced)

RocksDB data is stored in `veglenker.db/` directory. You can inspect it using RocksDB tools:

```bash
# Install ldb tool (macOS with Homebrew)
brew install rocksdb

# List column families
ldb --db=veglenker.db list_column_families

# Count keys in a column family
ldb --db=veglenker.db --column_family=VEGLENKER scan | wc -l

# Dump some keys
ldb --db=veglenker.db --column_family=VEGLENKER scan --max_keys=10
```

## Configuration

### Environment Variables

Override default configuration with environment variables:

```bash
# S3/MinIO configuration
export S3_ENDPOINT=http://localhost:9000
export S3_ACCESS_KEY=user
export S3_SECRET_KEY=password
export S3_BUCKET=nvdb-tnits-local-data-01

# NVDB API endpoints (usually don't need to change)
export UBERIKET_API_BASE_URL=https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/
export DATAKATALOG_API_BASE_URL=https://nvdbapiles.atlas.vegvesen.no/datakatalog/api/v1/
```

### Configuration File

Default configuration: `tnits-generator/src/main/resources/application.conf`

See: `config/AppConfig.kt:12` for configuration structure

## Project Structure

```
nvdb-tnits-public/
├── tnits-generator/          # Core CLI application
│   ├── src/
│   │   ├── main/kotlin/      # Application code
│   │   ├── test/kotlin/      # Tests
│   │   ├── main/resources/   # Configuration files
│   │   └── generated/java/   # Generated API models
│   └── build.gradle.kts      # Build configuration
├── tnits-katalog/            # REST API service
│   ├── src/main/kotlin/      # Spring Boot application
│   └── build.gradle.kts
├── buildSrc/                 # Shared build logic
├── docs/                     # Documentation
├── docker-compose.yml        # MinIO setup
├── README.md                 # Norwegian documentation
├── CLAUDE.md                 # AI coding assistant instructions
└── build.gradle.kts          # Root build configuration
```

## Troubleshooting

### Build Fails with "Could not resolve dependencies"

**Solution:** Clean Gradle cache and rebuild:

```bash
./gradlew clean --refresh-dependencies
./gradlew build
```

### MinIO Connection Errors

**Symptoms:** `Unable to connect to MinIO` errors

**Solution:** Check Docker is running:

```bash
docker ps | grep minio
```

If not running:

```bash
docker compose up -d
```

### RocksDB "Database is locked" Error

**Symptoms:** `RocksDB database is locked by another process`

**Solution:** Ensure no other instance is running:

```bash
# Find process
ps aux | grep tnits

# Kill if needed
kill <PID>
```

### Out of Memory Errors

**Symptoms:** `java.lang.OutOfMemoryError: Java heap space`

**Solution:** Increase JVM heap size:

```bash
export GRADLE_OPTS="-Xmx4g"
./gradlew run
```

### Tests Fail with "Port already in use"

**Symptoms:** Testcontainers port conflicts

**Solution:** Stop any running containers:

```bash
docker stop $(docker ps -aq)
./gradlew test
```

### NVDB API Rate Limiting

**Symptoms:** HTTP 429 errors during backfill

**Solution:** The application has built-in retry logic with exponential backoff. Just wait for it to complete. If it consistently fails, check NVDB API status.

## Next Steps

Now that you have the application running:

1. **Understand the architecture** - Read [Architecture Overview](ARCHITECTURE.md)
2. **Explore data flow** - Read [Data Flow Guide](DATA_FLOW.md)
3. **Learn about storage** - Read [Storage Architecture](STORAGE.md)
4. **Understand domain concepts** - Read [Concepts Glossary](CONCEPTS.md)
5. **Write tests** - Read [Testing Guide](TESTING.md)

## Development Workflow

Typical development workflow:

1. Create feature branch from `main`
2. Make code changes
3. Run tests: `./gradlew test`
4. Format code (automatic via git hooks)
5. Commit changes
6. Push and create pull request

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open project (File → Open → select root directory)
2. IntelliJ will auto-detect Gradle and import
3. Wait for indexing and dependency download
4. Run configurations should auto-generate

**Useful plugins:**

- Kotlin (bundled)
- Kotest (for test running)
- Save Actions (auto-format on save)

### VS Code

1. Install Java Extension Pack
2. Install Kotlin Language extension
3. Open project folder
4. Use integrated terminal for Gradle commands

## Getting Help

- **Architecture questions:** See [Architecture](ARCHITECTURE.md)
- **NVDB concepts:** See [Concepts Glossary](CONCEPTS.md)
- **Testing help:** See [Testing Guide](TESTING.md)
- **Project-specific:** Check `CLAUDE.md` for coding guidelines
- **NVDB API docs:** https://nvdbapiles.atlas.vegvesen.no/
