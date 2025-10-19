# Suggested Commands

## Build and Test
```bash
./gradlew build          # Full build including tests
./gradlew test           # Run tests only
./gradlew clean build    # Clean build
```

## Running the Application
```bash
./gradlew run                        # Auto mode (decides snapshot vs update)
./gradlew run --args="snapshot"      # Generate full snapshot
./gradlew run --args="update"        # Generate delta update
./gradlew run --args="inspire-roadnet"  # Export INSPIRE RoadNet
```

## Code Formatting
```bash
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Format code automatically
./gradlew installGitHooks    # Install pre-commit formatting hook
```

## Testing
```bash
# Run all tests
./gradlew test

# Run specific test class (NEVER use wildcards in --tests)
./gradlew test --tests="ExactClassName"

# Filter tests by name using environment variable
kotest_filter_tests="*test name pattern*" ./gradlew test --tests="ExactClassName" --info

# Example:
kotest_filter_tests="*should format with padded vegobjekttype*" ./gradlew test --tests="SpeedLimitExporterTest" --info

# Rerun tests
./gradlew test --rerun
```

## API Model Generation
```bash
./gradlew generateAllApiModels  # Generate models from OpenAPI specs
```

## Docker Services
```bash
docker compose up -d    # Start MinIO and other services
docker compose down     # Stop services
```

## NVDB API Testing
```bash
# Fetch a vegobjekt
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/85283803?inkluder=alle" | jq > vegobjekt-105-85283803.json

# Fetch multiple veglenkesekvenser
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=41423,42424" | jq > veglenkesekvenser-41423-42424.json
```

## Git Hooks
```bash
./gradlew installGitHooks  # Configure Git to use .hooks folder
```

## macOS Specific Notes
- System is Darwin (macOS)
- Standard Unix commands work (grep, find, ls, cd, etc.)
- Use `jq` for JSON formatting