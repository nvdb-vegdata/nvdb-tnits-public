# Codebase Structure

## Root Directory
```
nvdb-tnits-public/
├── docs/                   # Comprehensive documentation
├── tnits-generator/        # Main application module
├── tnits-katalog/          # Catalog service module
├── tnits-common/           # Shared code
├── buildSrc/               # Gradle build logic
├── .hooks/                 # Git hooks (pre-commit, etc.)
├── .run/                   # IntelliJ run configurations
├── docker-compose.yml      # Docker services (MinIO)
├── viewer.html             # TN-ITS data viewer (deployed to GitHub Pages)
└── CLAUDE.md               # Claude Code instructions
```

## Main Module: tnits-generator

### Source Structure
```
tnits-generator/src/main/kotlin/no/vegvesen/nvdb/tnits/generator/
├── Application.kt          # Main entry point
├── MainModule.kt           # Koin DI module
├── Globals.kt              # Global utilities
├── config/                 # Configuration classes
├── core/                   # Clean architecture core
│   ├── api/                # API client interfaces
│   ├── extensions/         # Kotlin extensions (geometry, etc.)
│   ├── model/              # Domain models
│   ├── presentation/       # DTOs for serialization
│   ├── services/           # Business logic services
│   └── useCases/           # Application use cases
└── infrastructure/         # External integrations
    ├── rocksdb/            # RocksDB storage implementation
    ├── nvdb/               # NVDB API client
    └── minio/              # MinIO/S3 client
```

## Key Packages

### Core Domain (`core/`)
- **useCases/**: High-level application workflows
  - `TnitsSnapshotCycle` - Full data snapshot
  - `TnitsUpdateCycle` - Incremental updates
  - `TnitsAutomaticCycle` - Automatic mode selection
- **services/**: Domain services and business logic
- **model/**: Domain entities and value objects
- **extensions/**: Kotlin extensions for geometry, etc.

### Infrastructure (`infrastructure/`)
- **rocksdb/**: RocksDB storage repositories
- **nvdb/**: NVDB API clients and models
- **minio/**: S3-compatible object storage

### Configuration (`config/`)
- Application configuration classes
- Environment-specific settings

## Test Structure
- Mirror production structure under `src/test/`
- Test resources follow naming patterns:
  - `vegobjekt-<type>-<id>.json`
  - `veglenkesekvens-<id>.json`
  - `veglenkesekvenser-<id1>-<idX>.json`