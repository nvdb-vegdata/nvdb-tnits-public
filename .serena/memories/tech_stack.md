# Tech Stack

## Language & Build
- **Language**: Kotlin (JVM)
- **Build Tool**: Gradle (Kotlin DSL)
- **Java Version**: Compatible with GraalVM for native compilation

## Core Dependencies
- **Dependency Injection**: Koin with annotations (`@Singleton`, `@KoinApplication`, `@Named`)
- **Storage**: RocksDB for persistent key-value storage
- **Logging**: SLF4J with custom `WithLogger` interface
- **HTTP Client**: OkHttp (via MinIO client)
- **Object Storage**: MinIO client for S3-compatible storage
- **Geospatial**: GeoTools for coordinate transformations and CRS handling
- **Testing**: Kotest framework

## Architecture
- **Clean Architecture**: Core domain separated from infrastructure
- **Package Structure**:
  - `core/` - Domain logic, use cases, services, models
  - `infrastructure/` - External integrations (RocksDB, APIs)
  - `config/` - Configuration classes

## Code Generation
- OpenAPI code generation for API models
- Gradle task: `generateAllApiModels`